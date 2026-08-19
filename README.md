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
Local LLM (Ollama: llama3.1:8b): rank, dedupe, summarize → top 20
        │
        ├──► Dashboard (REST API + web UI)
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
| LLM runtime | Ollama (local) — `llama3.1:8b` |
| Embedding model | Ollama — `nomic-embed-text` |
| Vector database | Chroma (Docker) |
| Relational storage | PostgreSQL (Docker) |
| News ingestion | RSS (Rome library) + optional NewsAPI |
| Scheduling | Spring `@Scheduled` cron, `Asia/Kolkata` |
| Notifications | Twilio WhatsApp Sandbox API |
| Containers | Docker Compose (Postgres + Chroma) |

## Project phases

| Phase | Scope | Status |
|---|---|---|
| 1 | Environment setup — Java, Docker, Ollama, Postgres, Chroma, Spring Boot smoke test | ✅ Done |
| 2 | News ingestion — RSS/NewsAPI fetcher → Postgres, with dedup | ✅ Done |
| 3 | Embeddings — articles → Ollama embeddings → Chroma vector store | ✅ Done |
| 4 | Agent — semantic retrieval → LLM ranking/dedup/summarization → top 20 | 🚧 In progress |
| 5 | Scheduler + dashboard — 7 AM IST cron, REST API, web UI | ⏳ Planned |
| 6 | WhatsApp notifier — top-5 short digest with dashboard link via Twilio | ⏳ Planned |

## How it works, stage by stage

1. **Ingestion** — fetches RSS feeds (TechCrunch, The Verge, Ars Technica, Hacker News, Wired) plus optionally NewsAPI, dedupes by article URL, stores raw articles in Postgres.
2. **Embedding** — every unprocessed article's title + content is embedded locally via Ollama's `nomic-embed-text` model and stored as a vector in Chroma, with metadata linking back to the Postgres row.
3. **Retrieval** — the agent queries Chroma with broad seed queries (e.g. "most significant AI and IT news today") to pull a candidate pool of ~40–50 articles.
4. **Ranking + summarization** — candidates are hydrated with full data from Postgres, then sent to the local LLM (`llama3.1:8b`) with instructions to rank by global significance, drop near-duplicate stories, and return exactly 20 results with short summaries as structured JSON.
5. **Digest storage** — the ranked top-20 result is persisted (`DailyDigest` entity) so the dashboard and WhatsApp notifier both read from the same run instead of recomputing.
6. **Scheduling** — a Spring cron job (`0 0 7 * * *`, zone `Asia/Kolkata`) runs the full pipeline automatically every morning.
7. **Dashboard** — a REST endpoint + simple web page shows the full top-20 list.
8. **WhatsApp notification** — a short digest (top 5 + link to the dashboard for the rest) is sent via Twilio right after the daily run completes.

## Prerequisites

- Java 21, Maven
- Docker Desktop
- Ollama (`brew install ollama`), with `llama3.1:8b` and `nomic-embed-text` pulled
- Twilio account (free WhatsApp sandbox) — for Phase 6

## Running locally

```bash
# 1. Start Postgres + Chroma
docker compose up -d

# 2. Start Ollama (if not already running as a service)
ollama serve

# 3. Build and run the app
mvn clean spring-boot:run
```

## Manual test endpoints (used during development)

| Endpoint | Purpose |
|---|---|
| `POST /api/ingest/run` | Fetch RSS feeds and save new articles to Postgres |
| `POST /api/embed/run` | Embed unprocessed articles into Chroma |
| `GET /api/embed/search?query=...` | Test semantic search against the vector store |
| `POST /api/agent/run` | Run retrieval + LLM ranking, return the top-20 digest |

Once Phase 5/6 are complete, these manual triggers are superseded by the daily scheduled run, and results are read from the dashboard endpoint instead.

## Project structure

```
com.raj.newsagent
├── config/          # Bean configuration (Ollama, Chroma)
├── controller/       # REST endpoints
├── model/            # JPA entities (Article, DailyDigest)
├── repository/        # Spring Data repositories
└── service/           # Ingestion, embedding, agent orchestration
```

## Notes

- This is a **learning project** — models and infra choices favor simplicity and zero cost (fully local) over production robustness.
- Local 7–8B models can occasionally produce malformed JSON output; the LLM-calling code includes basic fence-stripping/retry handling for this.