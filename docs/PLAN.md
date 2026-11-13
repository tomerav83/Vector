# Plan

Airport Investment Intelligence Agent: a chat agent that ranks and compares US airports for terminal-expansion investment.
The spec is in `FDE Exam 2.pdf`. The budget is about a day of work.

## How it answers a question

1. The LLM reads the question and picks a tool.
2. The tool (Java plus SQL over DuckDB) computes the answer.
3. The LLM writes the reply using only the numbers the tool returned.

The LLM never calculates. Scores are deterministic and covered by tests.
This is function calling, not vector RAG: the questions need exact sums and rankings across every airport, and similarity search can't provide that.

## Stages

| # | Stage | Status |
|---|---|---|
| 1 | Skeleton: Spring Boot, jOOQ/DuckDB, Spring AI, CI | done (PR #1) |
| 2 | Ingest: download public data, build tables, manifest | in progress |
| 3 | KPIs and scoring, unit-tested | |
| 4 | Tools, plus an MCP server in the dev profile | |
| 5 | Chat agent | |
| 6 | React chat UI | |
| 7 | Live APIs (NAS status, weather) | |
| 8 | Funding data: NPIAS, AIP, ATP, PFC | |
| 9 | Design doc | |
| 10 | Voice (bonus, browser Web Speech API) | |

## Stack

- Java 25, Spring Boot 4.1, Maven.
- Spring AI 2.0 for the LLM. The provider is set in config; dev uses the Gemini free tier because it's cheap.
- DuckDB through jOOQ. The SQL dialect is set in config.
- React/Vite for the UI.

## Data

- Public, keyless sources only: FAA NASR (airport register), FAA enplanements, FAA TAF (forecast), BTS T-100 (traffic), BTS delay causes, DOT fare premium.
- Nothing is committed. The app downloads into `data/cache/` and builds its tables at startup.
- `data/manifest-us.json` pins the period and fingerprint of each file. A mismatch is logged and noted in answers; it doesn't block startup.
- There is one official source per fact. NASR is the only source for airport identity; OurAirports was dropped.
- Airport ID is the ICAO code, or `US-<FAA code>` for the few airports without one. BTS codes are matched to NASR by coordinates.
- A country is a folder: `countries/<cc>/sources.yml` plus SQL that maps it onto the shared tables in `db/common-schema.sql`.

## Scoring

Weighted percentile within the FAA hub tier:

| KPI | Weight | Source |
|---|---|---|
| Growth (TAF 10-year boardings growth) | 25 | TAF |
| Congestion (delays, taxi-out) | 25 | BTS |
| Load factor (passengers / seats) | 20 | T-100 |
| Scale (added passengers) | 20 | TAF |
| Shortfall (scheduled flights not flown) | 10 | T-100 |
| Fare premium | extra | DOT (not available for AK/HI) |

- All KPIs live in one Java file as builder entries. The methodology version is a hash of that file.
- Analysts can change weights in chat. The LLM can't invent formulas.
- Constrained airports (SFO, SNA) are flagged instead of scored.
- Long-haul means 3,000 miles or more. Show all flights first, then cargo and passenger separately.

## Tools

`findAirports`, `rankAirports`, `airportMetrics(family)`, `routeMix`, `diagnoseCapacity` and `explain`, with `liveStatus` later.
Every result carries the period, `asOf`, caveats, a 0–3 confidence level, and its sources.

## Rules

- Ports and adapters only at external boundaries: DB, LLM, raw storage, live APIs. No interface that has only one implementation inside the core.
- An ArchUnit test stops the core from importing DuckDB, LLM providers or country code.
- CI runs on every push with small committed fixtures and no network or LLM. Live-data and LLM evals run weekly or by hand.
- Chat memory is in-memory for v1. Postgres would come in for multi-user production.

## Reference notes

Raw research notes from planning. Where they disagree with this file, this file wins.
- `notes/question-coverage.md`: 25 question types mapped to tools and data.
- `notes/tricky-inputs.md`: 125 tricky inputs (ambiguous names, false premises, out of scope) for testing.
