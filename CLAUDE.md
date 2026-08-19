# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A local, fully offline Java learning project: fetches AI/IT news via RSS, embeds and ranks it with a local LLM (Ollama) through a RAG-style pipeline, and emails the resulting top-20 digest. No cloud LLM APIs. See `README.md` for the full narrative/phase writeup — this file only covers what the README doesn't or gets wrong (the README has drifted from the code in a few places, noted below).

## Commands

```bash
# Build (compile only)
./mvnw clean compile

# Run the app (port 8581)
./mvnw spring-boot:run

# Run all tests
./mvnw test

# Run a single test class / method
./mvnw test -Dtest=AiNewsAgentApplicationTests
./mvnw test -Dtest=AiNewsAgentApplicationTests#contextLoads

# Package
./mvnw clean package
```

## Runtime dependencies (must be up before the app is useful)

- **PostgreSQL** on `localhost:5432` (db `postgres`, user/pass `postgres`/`postgres`) — raw articles + digest storage.
- **Ollama** on `localhost:11434` — needs `llama3:latest` (ranking) and `nomic-embed-text` (embeddings) pulled locally. Note: the README says `llama3.1:8b`; the actual code (`EmbeddingConfig`, `LlmTestController`) uses `llama3:latest`.
- **Chroma** on `localhost:8000` — vector store, collection name `news-articles`.

There is **no `docker-compose.yml` in this repo** despite the README's `docker compose up -d` instruction — Postgres/Chroma need to be started some other way (e.g. standalone Docker containers) before the app will boot successfully (Hikari fails fast if Postgres is unreachable).

## Architecture: the pipeline

Both the daily cron job and the manual "run everything" endpoint funnel through **`DailyPipelineScheduler.runPipeline()`** (`repository/DailyPipelineScheduler.java` — note: this class lives in the `repository` package despite not being a repository), which chains three independently-failing stages:

1. **Ingestion** — `RssFetchService.fetchAllAndSave()` (package `ai.news.ai_news_agent.RssFetchService`, capitalized to match the class name — non-standard but intentional, don't "fix" it into lowercase without checking imports). Pulls a hardcoded list of RSS feeds (TechCrunch, The Verge, Ars Technica, Hacker News, Wired), dedupes by `Article.url` (unique constraint), saves new rows.
2. **Embedding** — `EmbeddingIngestionService.embedUnprocessedArticles()`. Finds `Article`s with `processed=false`, embeds title+content via Ollama (`nomic-embed-text`), stores the vector in Chroma with `articleId`/`source`/`url`/`publishedAt` metadata, flips `processed=true`.
3. **Ranking** — `NewsAgentService.generateDailyTop20()`. Runs two broad seed-query searches against Chroma (`"most significant AI news today"`, `"most significant IT and technology news today"`), hydrates the matched `articleId`s back from Postgres, builds one big prompt listing all candidates, and asks the LLM (`llama3:latest`) to return a JSON array of exactly 20 `{rank, title, url, source, summary}` objects. The LLM's raw output is parsed with markdown-fence stripping and one retry (`extractJsonArray`) because local 7–8B models sometimes wrap JSON in prose or fences. Result is persisted as a `DailyDigest` row, then emailed.

Each stage's exception is caught independently in `runPipeline()` — one stage failing (e.g. a dead RSS feed, Ollama being down) does not stop the others from attempting to run.

`AgentController.POST /api/agent/run` calls `NewsAgentService.generateDailyTop20()` **directly**, skipping ingestion/embedding — it only works if Chroma/Postgres already have embedded articles from a prior pipeline run. `DigestController.POST /api/pipeline/run-now` runs the *full* three-stage pipeline. These are two different things that sound similar; check which one a task actually needs.

Scheduling: `@Scheduled(cron = "0 0 7 * * *", zone = "Asia/Kolkata")` in `DailyPipelineScheduler`.

## Gmail digest email — do not trust the README here

The README describes both an OAuth2 Gmail API flow and an env-var-based SMTP app-password flow. **Neither matches the current code.** The actual implementation:

- `config/GmailConfig.java` builds a `GmailCredentials` bean (username + app password) **only if** `gmail.enabled=true` (`@ConditionalOnProperty`). It fetches the app password by shelling out to the macOS `security` CLI: `security find-generic-password -a <gmail.username> -s <gmail.keychain-service> -w`.
- If the Keychain lookup fails (missing entry, wrong service/account name, not on macOS), `GmailConfig` catches the exception, logs a warning with the exact `security add-generic-password ...` command needed to fix it, and returns `null` — the app still starts, `GmailService` just no-ops digest emails.
- `service/GmailService.java` sends plain SMTP (`smtp.gmail.com:587`, STARTTLS) via Jakarta Mail — **not** the Gmail REST API.
- Relevant `application.properties` keys: `gmail.enabled`, `gmail.username`, `gmail.keychain-service`, `gmail.recipient`, `gmail.sender-name`, `gmail.smtp-host`, `gmail.smtp-port`.
- One-time local setup (not something Claude should run without asking, since it writes a secret to the user's Keychain):
  ```bash
  security add-generic-password -a <gmail.username> -s <gmail.keychain-service> -w '<16-char-app-password>'
  ```
  The password must be a Google **App Password** (requires 2-Step Verification on the account), not the account's normal login password.

## REST endpoints

| Endpoint | Does |
|---|---|
| `POST /api/ingest/run` | RSS fetch + save only |
| `POST /api/embed/run` | Embed unprocessed articles into Chroma only |
| `GET /api/embed/search?query=...` | Ad-hoc semantic search test against Chroma |
| `POST /api/agent/run` | Retrieval + LLM ranking + email — **assumes ingestion/embedding already ran** |
| `POST /api/pipeline/run-now` | Full pipeline (ingest → embed → rank → email), same as the 7 AM cron |
| `GET /api/digest/latest` | Most recently persisted `DailyDigest` JSON |
| `GET /test-llm` | Throwaway sanity check that Ollama is reachable (`LlmTestController`, sits at the root package, not `controller/`) |

## Known rough edges

- `LlmTestController` and `DailyPipelineScheduler` are misplaced relative to the package structure implied by `config/`, `controller/`, `service/`, `repository/` (root package and `repository/` respectively) — this is existing/intentional-so-far project state, not something to silently "clean up" mid-task.
- No lint/formatter config present (no Checkstyle/Spotless in `pom.xml`).
