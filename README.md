# agents
This is repo for agent building and testing
# AI News Agent (Java + Local LLM)

A local, fully offline learning project: a Java-based AI agent that pulls daily AI/IT news, ranks and summarizes it using a local LLM through a RAG pipeline, and (eventually) delivers a top-20 dashboard plus a WhatsApp digest — every day at 7 AM IST.

No cloud LLM APIs, no API keys required for the core pipeline — everything runs on your Mac via Ollama.

## Why this project

Built to learn the full AI-agent stack hands-on in Java: ingestion, embeddings, vector search, RAG, LLM-based reasoning/ranking, scheduling, and notification — each stage implemented explicitly rather than hidden behind a framework.

## Architecture / flow

```
RSS / NewsAPI sources
        │
        ▼
Ingestion service  ───────►  Postgres (raw articles, deduped by URL)
        │
        ▼
Embedding service (Ollama: nomic-embed-text)
        │
        ▼
Chroma vector store (semantic search index)
        │
        ▼
Agent (LangChain4j): retrieve candidates → hydrate from Postgres
        │
        ▼
Local LLM (Ollama: llama3:latest): rank, dedupe, summarize → top 20
        │
        ├──► Dashboard (REST API + web UI)
        │
        ├──► Gmail notifier (SMTP, app password from macOS Keychain): full digest emailed after every run
        │
        └──► WhatsApp notifier (Twilio): top 5 short digest + dashboard link
        ▲
        │
Scheduler (Spring @Scheduled, cron, Asia/Kolkata) — triggers the full run daily at 7 AM IST
```

## Tech stack

| Layer | Technology |
|---|---|
| Language / framework | Java 21, Spring Boot 3 |
| Agent orchestration | LangChain4j |
| LLM runtime | Ollama (local) — `llama3:latest` |
| Embedding model | Ollama — `nomic-embed-text` |
| Vector database | Chroma (Docker) |
| Relational storage | PostgreSQL (Docker) |
| News ingestion | RSS (Rome library) + optional NewsAPI |
| Scheduling | Spring `@Scheduled` cron, `Asia/Kolkata` |
| Email notifications | SMTP (Jakarta Mail) via `smtp.gmail.com`, app password read from macOS Keychain |
| Notifications | Twilio WhatsApp Sandbox API |
| Containers | Docker (Postgres + Chroma, run standalone — see "Running locally") |

## Project phases

| Phase | Scope | Status |
|---|---|---|
| 1 | Environment setup — Java, Docker, Ollama, Postgres, Chroma, Spring Boot smoke test | ✅ Done |
| 2 | News ingestion — RSS/NewsAPI fetcher → Postgres, with dedup | ✅ Done |
| 3 | Embeddings — articles → Ollama embeddings → Chroma vector store | ✅ Done |
| 4 | Agent — semantic retrieval → LLM ranking/dedup/summarization → top 20 | ✅ Done |
| 5 | Scheduler + dashboard — 7 AM IST cron, REST API, web UI | ✅ Done |
| 6 | WhatsApp notifier — top-5 short digest with dashboard link via Twilio | ⏳ Planned |

## How it works, stage by stage

1. **Ingestion** — fetches RSS feeds (TechCrunch, The Verge, Ars Technica, Hacker News, Wired) plus optionally NewsAPI, dedupes by article URL, stores raw articles in Postgres.
2. **Embedding** — every unprocessed article's title + content is embedded locally via Ollama's `nomic-embed-text` model and stored as a vector in Chroma, with metadata linking back to the Postgres row.
3. **Retrieval** — the agent queries Chroma with broad seed queries (e.g. "most significant AI and IT news today") to pull a candidate pool of ~40–50 articles.
4. **Ranking + summarization** — candidates are hydrated with full data from Postgres, then sent to the local LLM (`llama3:latest`) with instructions to rank by global significance, drop near-duplicate stories, and return exactly 20 results with short summaries as structured JSON.
5. **Digest storage** — the ranked top-20 result is persisted (`DailyDigest` entity) so the dashboard reads from the same run instead of recomputing.
6. **Scheduling** — a Spring cron job (`0 0 7 * * *`, zone `Asia/Kolkata`) runs the full pipeline automatically every morning.
7. **Dashboard** — `GET /api/digest/latest` + `static/dashboard.html` show the full top-20 list.
8. **Gmail digest email** — right after ranking, the full top-20 digest is emailed over SMTP (Jakarta Mail, `smtp.gmail.com:587`) using a Gmail app password read from the macOS Keychain at startup — covers both the manual `/api/agent/run` call and the daily scheduled run. See "Gmail digest setup" below.
9. **WhatsApp notification** — planned, not yet implemented (Phase 6).

## Prerequisites

- Java 21, Maven
- Docker Desktop
- Ollama (`brew install ollama`), with `llama3:latest` and `nomic-embed-text` pulled
- macOS (the Gmail app password is read from the macOS Keychain — see below)
- Twilio account (free WhatsApp sandbox) — for Phase 6

## Gmail digest setup

The agent emails the ranked digest over Gmail's SMTP server after every run.
No Google Cloud project or OAuth setup needed — just a Gmail **app
password**, stored in the macOS Keychain so it's never written to disk in
this repo or committed.

1. On the sending Gmail account, enable **2-Step Verification**
   (Google Account → Security).
2. Go to Google Account → Security → **App passwords**, generate one for
   "Mail", and copy the 16-character password.
3. Store it in the macOS Keychain once, matching the `gmail.username` /
   `gmail.keychain-service` values in `application.properties`:
   ```bash
   security add-generic-password \
     -a your.account@gmail.com \
     -s ai-news-agent-gmail \
     -w '<16-char-app-password>'
   ```
4. Adjust `gmail.recipient` in `application.properties` if the destination
   address should differ from the sending account.

At startup, `GmailConfig` runs `security find-generic-password` to read the
password — it never touches disk or version control. If the Keychain entry
is missing or `gmail.enabled=false`, the app still starts normally; it just
logs a warning and skips sending digest emails.

## Running locally

```bash
# 1. Start Postgres + Chroma
# (no docker-compose.yml in this repo yet — standalone containers, matching
# the ports/credentials in application.properties)
docker run -d --name ai-news-postgres -p 5432:5432 \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres postgres:16
docker run -d --name ai-news-chroma -p 8000:8000 chromadb/chroma

# 2. Start Ollama (if not already running as a service)
ollama serve

# 3. Build and run the app
./mvnw clean spring-boot:run
```

## API endpoints

| Endpoint | Purpose |
|---|---|
| `POST /api/ingest/run` | Fetch RSS feeds and save new articles to Postgres |
| `POST /api/embed/run` | Embed unprocessed articles into Chroma |
| `GET /api/embed/search?query=...` | Test semantic search against the vector store |
| `POST /api/agent/run` | Retrieval + LLM ranking + email only — assumes ingestion/embedding already ran |
| `POST /api/pipeline/run-now` | Full pipeline (ingest → embed → rank → email) — same flow as the 7 AM cron, useful for testing without waiting |
| `GET /api/digest/latest` | Latest persisted top-20 digest as JSON — powers the dashboard |
| `GET /test-llm` | Quick sanity check that Ollama is reachable |

Once Phase 6 (WhatsApp) is complete, the dashboard (`/static/dashboard.html`) and the daily scheduled run become the primary way to use the app day-to-day; the individual stage endpoints above remain useful for debugging one stage at a time.

## Project structure

```
ai.news.ai_news_agent
├── config/            # Bean configuration (Ollama, Chroma, Gmail/SMTP)
├── controller/        # REST endpoints
├── model/             # JPA entities (Article, DailyDigest, RankedArticle)
├── repository/        # Spring Data repositories + DailyPipelineScheduler (cron entry point)
├── service/           # Embedding ingestion, agent ranking, Gmail sending
└── RssFetchService/    # RSS ingestion (package name matches the single class it holds)
```

## Notes

- This is a **learning project** — models and infra choices favor simplicity and zero cost (fully local) over production robustness.
- Local 7–8B models can occasionally produce malformed JSON output; the LLM-calling code includes basic fence-stripping/retry handling for this.