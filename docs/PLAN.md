# Plan

Airport Investment Intelligence Agent: a chat agent that ranks and compares US airports for terminal-expansion investment.
The spec is in `FDE Exam 2.pdf`. The budget is about a day of work.

## How it answers a question

1. The LLM reads the question, decides what data it needs and calls tools, often several in a row. Each tool returns one kind of data: traffic, delays, forecast, facilities or live status.
2. The tools compute numbers deterministically, from the loaded snapshot or from a live public API.
3. For rankings, the LLM calls `scoreAirports`. Java owns the scoring formula.
4. The LLM combines the evidence, answers "why" questions by reasoning over it, cites the numbers it used, and states its assumptions and how sure it is.

## Where AI is used, and where it isn't

| AI (LLM) | Deterministic (Java + SQL) |
|---|---|
| Interpreting loose questions ("LA", "strong candidate", "long-haul") | Every number and KPI |
| Choosing tools and arguments, chaining calls | Scores and rankings |
| Reasoning over evidence for "why" questions | Name resolution candidates |
| Follow-ups, clarifying questions, declining out-of-scope questions | Data loading and validation |
| Writing the answer with sources, assumptions and confidence | |

This is function calling, not vector RAG: the questions need exact sums and rankings across every airport, and similarity search can't provide that.

## Stages

| # | Stage | Status |
|---|---|---|
| 1 | Skeleton: Spring Boot, jOOQ/DuckDB, Spring AI, CI | done (PR #1) |
| 2 | Data platform: download public data, build tables, manifest | in progress, kept as designed |
| 3 | Vertical slice: data tools, basic scoring, agent, plain chat page. Done when all four exam questions get a sensible answer | |
| 4 | Full scoring: KPI registry, golden-value tests, weights changeable in chat | |
| 5 | Live API tools (NAS status, weather, recent months from the BTS/DOT data APIs) | |
| 6 | React chat UI that shows each tool call (tool, arguments, source, asOf) | |
| 7 | Funding data: NPIAS, AIP, ATP, PFC | |
| 8 | Design doc | |
| 9 | Voice (bonus, browser Web Speech API) | |

The slice comes before the depth: a working agent early, then better scores.

## Stack

- Java 25, Spring Boot 4.1, Maven.
- Spring AI 2.0 for the LLM. The provider is set in config; dev uses the Gemini free tier because it's cheap.
- DuckDB through jOOQ. The SQL dialect is set in config.
- React/Vite for the UI.

## Data

- **Snapshot**, for bulk files with no queryable API: FAA NASR (airport register), FAA enplanements, FAA TAF (forecast), BTS T-100 (traffic), BTS delay causes, DOT fare premium. Scores are computed only from the snapshot, so they stay reproducible.
- **Live**, called by the agent at question time: FAA NAS status, weather, and BTS/DOT data APIs for months newer than the snapshot. Never used in scores. Each answer labels the data as live.
- Nothing is committed. The app downloads into `data/cache/` and builds its tables at startup.
- `data/manifest-us.json` pins the period and fingerprint of each file. A mismatch is logged and noted in answers; it doesn't block startup.
- There is one official source per fact. NASR is the only source for airport identity; OurAirports was dropped.
- Airport ID is the ICAO code, or `US-<FAA code>` for the few airports without one. BTS codes are matched to NASR by coordinates.
- A country is a folder: `countries/<cc>/sources.yml` plus SQL that maps it onto the shared tables in `db/common-schema.sql`.

## Tools

Tools are split by data capability, not by question type, so the agent can combine them for questions nobody planned for.

| Tool | Returns |
|---|---|
| `findAirports(query)` | Matching airports for a name, code, city, metro or region, flagging ambiguity ("Portland") |
| `getTraffic(airports, period)` | Passengers, seats, load factor, flights scheduled vs flown, route distances (long-haul share) |
| `getDelays(airports, period)` | Delay rates, causes, taxi-out, cancellations |
| `getForecast(airports)` | FAA TAF boardings and operations, growth |
| `getFacilities(airports)` | Runways, known caps, slot control, curfews |
| `scoreAirports(airports, weights)` | Deterministic score, per-KPI breakdown and rank |
| `getLiveStatus(airports)` | Current delays, ground stops, arrival rate, weather (stage 5) |
| `explain(topic)` | Methodology, KPI definitions, data limits |

Every result carries the period, `asOf`, caveats, a 0–3 confidence level, and its sources.
Example: for "unmet demand at SFO and why", the agent combines traffic (shortfall, load factor), delays (causes), facilities (runway layout, arrival rate) and live status, and then argues the cause from that evidence.

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

## Rules

- Ports and adapters only at external boundaries: DB, LLM, raw storage, live APIs. No interface that has only one implementation inside the core.
- An ArchUnit test stops the core from importing DuckDB, LLM providers or country code.
- CI runs on every push with small committed fixtures and no network or LLM. Live-data and LLM evals run weekly or by hand.
- Chat memory is in-memory for v1. Postgres would come in for multi-user production.

## Reference notes

Raw research notes from planning. Where they disagree with this file, this file wins. The question-coverage note still lists the old tools organized by question type.
- `notes/question-coverage.md`: 25 question types mapped to tools and data.
- `notes/tricky-inputs.md`: 125 tricky inputs (ambiguous names, false premises, out of scope) for testing.
