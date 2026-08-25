# Smart Log Analyzer & Anomaly Detector

**🔴 Live demo:** [logsentinel-7uz1.onrender.com](https://logsentinel-7uz1.onrender.com/)
username : admin
password : admin
> Hosted on Render's free tier — if the app has been idle, the first request may take 30–60 seconds while the instance spins back up.

A full-stack application that ingests web-server access logs, flags unusual
entries using its **own** statistical anomaly-detection algorithm, persists every
flagged entry with a reason and a score, and then uses an LLM **only** to turn
each already-flagged entry into a plain-English explanation with a likely root
cause and next step. A React UI shows the log timeline with anomalies highlighted
and a detail view for each entry.

> **Design principle (important):** The AI never decides what is anomalous.
> Detection is done entirely by deterministic code in
> `detector/AnomalyDetector.java`. The AI is handed entries that our algorithm has
> *already* flagged and is asked purely to explain them. This separation is
> deliberate and is enforced by the code and the system prompt.

---

## Table of contents

1. [What it does](#what-it-does)
2. [Architecture](#architecture)
3. [Tech stack & prerequisites](#tech-stack--prerequisites)
4. [Quick start](#quick-start)
5. [AI configuration](#ai-configuration)
6. [The anomaly-detection approach](#the-anomaly-detection-approach)
7. [How the AI is used](#how-the-ai-is-used)
8. [REST API](#rest-api)
9. [Data model & persistence](#data-model--persistence)
10. [Validation & edge cases](#validation--edge-cases)
11. [Dataset generator & verification script](#dataset-generator--verification-script)
12. [Testing](#testing)
13. [Project structure](#project-structure)
14. [Assumptions](#assumptions)
15. [Limitations](#limitations)

---

## What it does

The application satisfies the assessment requirements end to end:

- **Ingests and persists log data.** Upload a CSV through the UI (or let the app
  seed the bundled sample on first launch). Rows are parsed, validated, and stored
  in PostgreSQL.
- **Flags unusual entries with its own algorithm.** A data-driven, weighted-signal
  detector learns what "normal" looks like from the dataset and scores every entry
  from `0.0` to `1.0`. Entries at or above the threshold (`0.70`) are flagged.
- **Persists each flagged entry with a reason and score.** Every log row stores
  `is_anomaly`, `anomaly_score`, a human-readable `anomaly_reason`, and a JSON
  breakdown of which signals fired and by how much.
- **Uses AI only to explain.** For any flagged entry, the app can call an LLM to
  produce `{ explanation, root_cause, next_step }` in plain English. If no API key
  is configured, a deterministic offline explainer produces the same shape so the
  app is fully functional without any network access.
- **Provides a UI.** A dashboard with summary stats, a filterable/paginated log
  table (anomalies highlighted in red), and a per-entry detail page showing the
  score breakdown and the AI explanation.
- **Handles bad input.** Missing/malformed timestamps, malformed rows, unknown
  columns, and empty datasets are all handled gracefully with clear messages.

---

## Architecture

```
                 ┌──────────────────────────┐
   CSV upload →  │  React + Vite frontend    │  (port 5173)
                 │  dashboard · table · detail│
                 └────────────┬──────────────┘
                              │ REST/JSON (/api/**)
                 ┌────────────▼──────────────┐
                 │   Spring Boot backend      │  (port 8080)
                 │                            │
                 │  ingest → validate → store │
                 │        │                   │
                 │        ▼                   │
                 │  AnomalyDetector (OUR code)│  ← decides anomalies
                 │        │  flags + score    │
                 │        ▼                   │
                 │  AiExplanationService      │  ← explains ONLY
                 │   ├─ OpenAiClient (live)   │
                 │   └─ OfflineExplainer      │
                 └────────────┬──────────────┘
                              │ JPA / Hibernate
                 ┌────────────▼──────────────┐
                 │      PostgreSQL 16          │  (Docker, port 5432)
                 └────────────────────────────┘
```

The detector is intentionally decoupled from Spring and JPA: it depends only on a
small `LogView` interface and the JDK, so the entire algorithm can be unit-tested
without a database or application context.

---

## Tech stack & prerequisites

**Backend:** Java 17, Spring Boot 3.3.5 (Web, Data JPA, Validation), Hibernate,
PostgreSQL driver, JUnit 5. The OpenAI call uses the JDK's built-in
`java.net.http.HttpClient` — no extra HTTP dependency.

**Frontend:** React 18, Vite 5, React Router 6, Axios.

**Database:** PostgreSQL 16 (via Docker Compose). H2 is used for tests only.

To build and run locally you need:

| Tool                | Version | Notes                                    |
|---------------------|---------|------------------------------------------|
| JDK                 | 17+     | Backend targets Java 17.                 |
| Maven               | 3.8+    | Or use the bundled `mvnw` if present.    |
| Node.js + npm       | 18+     | For the Vite frontend.                   |
| Docker + Compose    | recent  | For PostgreSQL (or supply your own DB).  |
| Python (optional)   | 3.8+    | Only for the dataset/verify scripts.     |

---

## Quick start

Want to try it without setting anything up? Use the live demo:
**https://logsentinel-7uz1.onrender.com/**

To run it locally instead:

### 1. Start PostgreSQL

```bash
docker compose up -d
```

This starts PostgreSQL 16 on `localhost:5432` with database `loganalyzer` and
user/password `loganalyzer`/`loganalyzer` (matching the backend defaults).

### 2. Run the backend

```bash
cd backend
# optional: configure AI and DB overrides (see backend/.env.example)
export OPENAI_API_KEY=sk-...        # omit to run in offline mode
mvn spring-boot:run
```

The backend starts on `http://localhost:8080`. On first launch (when the table is
empty) it seeds the bundled `sample-logs.csv` — 10,000 rows containing two injected
anomalies — and runs a detection scan automatically.

### 3. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The Vite dev server proxies `/api/**` to the backend,
so no CORS setup is needed in development.

### 4. Try it

You should immediately see stats and a populated table with anomalies highlighted.
Click any flagged row to open its detail page, then use **Explain with AI** to
generate a plain-English analysis. Upload your own CSV with the **Upload** button;
the detector re-scans the full dataset after each upload.

---

## AI configuration

The AI layer is controlled entirely by environment variables (all optional; every
one has a default). See `backend/.env.example`.

| Variable          | Default                     | Meaning                                   |
|-------------------|-----------------------------|-------------------------------------------|
| `OPENAI_API_KEY`  | *(empty)*                   | If blank → offline mode. If set → live.   |
| `AI_PROVIDER`     | `openai`                    | Provider selector.                        |
| `AI_MODEL`        | `gpt-4o-mini`               | Chat model used for explanations.         |
| `AI_BASE_URL`     | `https://api.openai.com/v1` | Override for Azure/OpenAI-compatible APIs.|

**Live mode.** When `OPENAI_API_KEY` is set, the backend calls the OpenAI Chat
Completions API (`response_format: json_object`, low temperature) and parses a
strict JSON object.

**Offline mode.** When no key is present — or if any API call fails or times out —
the app falls back to `OfflineExplainer`, a deterministic component that composes an
explanation from the detector's own signal output. This guarantees the app is fully
demoable with **zero** external dependencies, and it means an AI outage never breaks
the product. The UI shows a badge indicating whether AI is *live* or *offline*, and
each stored analysis records which provider produced it.

---

## The anomaly-detection approach

This is the heart of the project, so it is worth explaining in full.

### Why not simple rules?

The obvious approach — "flag every HTTP 500" or "flag every failed login" — fails on
realistic data. In the provided sample the HTTP status codes are spread roughly
evenly, so **~20% of rows are 5xx**. Flagging all of them would flag a fifth of the
dataset as "anomalous," which is useless. Anomalies are things that are *unusual
relative to the rest of the data*, not things that match a fixed blacklist. So the
detector learns the baseline from the data and flags statistical outliers.

### Two phases: fit, then score

1. **`fit(logs)`** learns baselines from the whole dataset: the frequency of each
   categorical value (location, user-agent), the number of requests per source IP,
   a sorted timeline of events per IP, and — crucially — the *mean and standard
   deviation of per-IP request volume*. Nothing is hard-coded to a magic value.
2. **`score(entry)`** combines four normalized signals into a single `0..1` score
   via a weighted sum, then flags the entry if the score ≥ the threshold.

### The four signals

Each signal returns a raw score in `[0, 1]`, which is multiplied by its weight. The
final score is `min(1.0, Σ weighted)`.

| Signal            | Weight | What it measures                                                                 |
|-------------------|:------:|----------------------------------------------------------------------------------|
| **Rarity**        | 0.85   | How statistically rare the entry's location / user-agent is (normalized surprisal). A value seen in ≥5% of traffic scores 0; rarity ramps up as the value approaches ~0.1% frequency. |
| **Source volume** | 0.85   | How far this IP's total request count sits above the per-source baseline. Zero until `mean + 3σ`, saturating at `mean + 10σ`. Catches one IP hammering the server. |
| **Severity**      | 0.30   | A minor nudge for error statuses (5xx = 1.0, 4xx = 0.4). Intentionally *cannot* flag an entry on its own — a lone common error stays below threshold. |
| **Burst**         | 0.45   | How many requests the same IP made within a ±10-minute window (via binary search on the sorted per-IP timeline). Catches spikes even from an IP whose overall count is modest. |

The weights are chosen so that a single strong structural signal (a genuinely rare
value, or a clear volume outlier) is enough to cross the `0.70` threshold, while
severity alone is not. This keeps the false-positive rate low and makes flags
explainable. All weights and thresholds live in `application.yml` under
`app.detector.*` and can be tuned without touching code.

### What it produces

Running the detector over the bundled sample flags **59 of 10,000 rows (0.59%)**:

- All **10** rows from the injected rare location (`North Korea`, 0.1% of traffic) —
  caught by the **rarity** signal.
- All **49** requests from the injected high-volume IP (`15.6.62.53`, vs. a baseline
  of ~1 request per IP) — caught by the **source-volume** signal.
- **Zero** false positives from ordinary individually-common errors.

Every flag is accompanied by a plain-language reason (e.g. *"Rare location 'North
Korea' — seen in only 0.10% of traffic"*) and a per-signal JSON breakdown that the
UI renders as a bar chart.

### Independent verification

Because the detector depends only on the JDK, its behaviour is verified two ways:

- **JUnit tests** (`AnomalyDetectorTest`) assert that common 500s are *not* flagged,
  rare locations and volume outliers *are* flagged, the overall anomaly rate stays
  small, and scores are deterministic.
- **A Python mirror** (`scripts/verify_detector.py`) reimplements the exact same
  algorithm and parameters, so you can cross-check the results on any CSV from the
  command line without a JVM.

---

## How the AI is used

The AI's role is strictly limited to **explanation of entries the detector already
flagged**. It is never asked "is this an anomaly?"

The flow (`AiExplanationService`):

1. The detector flags an entry and stores its score, reason, and signal breakdown.
2. When explanation is requested, the service builds a prompt containing the log's
   fields **plus our detector's score, reason, and signals**, and instructs the
   model: *you are given an entry our system has ALREADY flagged; do not re-judge
   whether it is anomalous; explain it and suggest a root cause and next step.*
3. The model must return strict JSON: `{ "explanation", "root_cause", "next_step" }`.
4. On success the result is persisted (`ai_analysis` table, `provider = "openai"`).
   On missing key, timeout, or any error, `OfflineExplainer` returns the same shape
   (`provider = "offline"`), derived deterministically from the detector's signals.

Explanations are generated, not templated — in live mode they are free-form model
output; the offline path is a graceful fallback, clearly labelled as such.

---

## REST API

All endpoints are under `/api`.

| Method | Path                     | Purpose                                                        |
|--------|--------------------------|----------------------------------------------------------------|
| `POST` | `/api/logs/upload`       | Upload a CSV (multipart `file`); parses, validates, stores, scans. |
| `GET`  | `/api/logs`              | List logs. Filters: `anomaly`, `status`, `q`; paging: `page`, `size`; sorting: `sort`, `dir`. |
| `GET`  | `/api/logs/{id}`         | Get a single log entry (with its AI analysis if present).      |
| `POST` | `/api/logs/{id}/analyze` | Generate (or refresh) the AI explanation for a flagged entry.  |
| `POST` | `/api/logs/rescan`       | Re-run detection over all stored logs.                         |
| `DELETE`| `/api/logs`             | Clear all logs.                                                |
| `GET`  | `/api/anomalies`         | List only flagged entries (sorted by score desc by default).   |
| `GET`  | `/api/stats`             | Summary counts: total, anomalies, rate, error counts, status distribution. |
| `GET`  | `/api/meta`              | AI provider/live status, model, detector threshold (drives the UI badge). |

Errors are returned as a consistent JSON envelope via a global exception handler
(400 for bad input, 404 for unknown IDs, 413 for oversized uploads, 500 otherwise).

---

## Data model & persistence

Two tables (created automatically by Hibernate `ddl-auto=update`):

- **`log_entry`** — one row per ingested log line: `timestamp`, `ip_address`,
  `request_type`, `status_code`, `user_agent`, `session_id`, `location`, `message`,
  plus detector output `is_anomaly`, `anomaly_score`, `anomaly_reason`,
  `signal_breakdown` (JSON), and `created_at`. Indexed on `is_anomaly`, `timestamp`,
  and `ip_address`.
- **`ai_analysis`** — one-to-one with a flagged `log_entry`: `explanation`,
  `root_cause`, `next_step`, `provider`, `model`, `created_at`.

---

## Validation & edge cases

Handled explicitly (see `LogValidator`, `CsvLogParser`, `LogIngestionService`):

- **Missing / malformed timestamp** — the row is rejected and reported; several
  timestamp formats are tried (`yyyy-MM-dd HH:mm:ss`, ISO-8601, etc.).
- **Malformed status code** — rejected with a reason; severity words (`INFO`,
  `WARN`, `ERROR`) are also mapped to representative codes when present.
- **Unknown / re-ordered columns** — a synonym map normalizes common header names,
  so `IP_Address`, `ip`, `client_ip`, etc. all map to the same field. If *no*
  recognizable columns are found, the upload is rejected with a clear message.
- **Empty dataset** — returns `400` with an explanatory message rather than crashing.
- **Partial failures** — valid rows are still ingested; up to 100 per-row validation
  issues are collected and returned in the upload response so the user sees exactly
  what was skipped and why.
- **Oversized uploads** — capped (25 MB) and reported as `413`.

---

## Dataset generator & verification script

Two optional Python utilities live in `scripts/` (standard library only):

```bash
# Generate a fresh, mostly-normal dataset with two injected anomalies
python scripts/generate_dataset.py --rows 10000 --seed 42 --out data/generated-logs.csv

# Cross-check the detector's output on any CSV (mirrors the Java algorithm)
python scripts/verify_detector.py data/log-data.csv
```

`generate_dataset.py` produces realistic traffic (≈78% HTTP 200, near-unique client
IPs, weighted locations/user-agents) and injects a rare location and a high-volume
IP so you can watch the detector light up. `verify_detector.py` is a line-for-line
port of `AnomalyDetector.java` used to validate behaviour without a JVM.

---

## Testing

```bash
cd backend
mvn test
```

Covers the detector (`AnomalyDetectorTest`), CSV parsing (`CsvLogParserTest`),
validation (`LogValidatorTest`), and application context startup
(`LogAnalyzerApplicationTests`, using H2 with seeding disabled).

---

## Project structure

```
smart-log-analyzer/
├─ docker-compose.yml            # PostgreSQL 16
├─ backend/
│  ├─ pom.xml
│  ├─ .env.example
│  └─ src/
│     ├─ main/java/com/digiplus/loganalyzer/
│     │  ├─ detector/     # AnomalyDetector (+ config/results) — OUR algorithm
│     │  ├─ ingest/       # CSV parser + validator
│     │  ├─ ai/           # OpenAI client + offline explainer + service
│     │  ├─ entity/       # LogEntry, AiAnalysis (JPA)
│     │  ├─ repository/   # Spring Data repositories
│     │  ├─ service/      # ingestion, scan, mapping, query services
│     │  ├─ controller/   # REST controllers
│     │  ├─ config/       # properties binding, CORS, data seeder
│     │  └─ dto/ · exception/
│     ├─ main/resources/  # application.yml, sample-logs.csv
│     └─ test/            # JUnit 5 tests (+ H2 test config)
├─ frontend/
│  ├─ package.json · vite.config.js · index.html
│  └─ src/                # App, pages (Dashboard, LogDetail), components, api client
├─ data/                  # sample + generated CSVs
└─ scripts/               # generate_dataset.py, verify_detector.py
```

---

## Assumptions

- The provided CSV schema (`Timestamp, IP_Address, Request_Type, Status_Code,
  User_Agent, Session_ID, Location`) is representative; the parser also tolerates
  common header variants and a free-text `message` column if present.
- Timestamps without a timezone are treated as UTC (consistently, for both storage
  and burst-window math).
- The sample data is illustrative only. The detector learns its baseline from
  whatever data is loaded, so it adapts to a different dataset without code changes.
- "Anomaly" means *statistically unusual relative to the current dataset*, which is
  the appropriate definition for unsupervised log analysis where no labels exist.

---

## Limitations

- **Detection is unsupervised and dataset-relative.** With no ground-truth labels,
  the detector optimizes for catching structural outliers (rare values, volume
  spikes, bursts) rather than semantic attacks that look statistically normal. It is
  a strong, explainable baseline, not a replacement for a trained IDS.
- **Batch, in-memory fit.** `fit` loads the current dataset to compute baselines,
  which is ideal for the assessment's scale (tens of thousands of rows) but would
  need windowing/streaming for very large or continuous feeds.
- **Single-node.** No auth, rate limiting, or multi-tenant separation — out of scope
  for the exercise.
- **AI quality depends on the model.** Live explanations vary with the chosen model;
  the offline fallback is deterministic but necessarily more generic.
- **Threshold/weights are heuristic.** They are tuned to the sample and exposed in
  configuration; a production deployment would calibrate them against labelled data.
