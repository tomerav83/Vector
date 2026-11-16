# Design

Vector is a chat agent for analysts at a firm that invests in US airport terminals. It ranks and compares airports
from public FAA, BTS and DOT data. **Every number it gives is computed by SQL or Java; the language model only picks
the tools and writes the answer.**

This page is the short design document the brief asks for. It covers the architecture, the scoring method, the key
tradeoffs and where AI is used. Each section links to a longer page.

## How a question is answered

```
browser ──POST /api/chat──▶ Agent (Spring AI ChatClient + chat memory)
                              │  the LLM picks tools, often several in a row
                              ▼
                 Airports: find · Traffic: traffic, longHaul · Congestion · Growth · Runways
                 Scores: rank
                              │  SQL over the views of sql/views.sql, the newest 12 months
                              ▼
                 DuckDB, in memory, built at startup from the public files (data package)
```

1. The model reads the question and turns names into codes with `find` ("LA" → LAX, "Logan" → BOS).
2. It calls the tools it needs. Each returns rows, plus the period they cover, the state of their sources,
   definitions and limitations. The answer streams back as server-sent events, so while the model works the page
   lists each tool it calls ("✓ Reading BTS traffic"), as ChatGPT and Claude show their steps.
3. For "which are strong candidates", it calls `rank`, which scores the airports in Java.
4. It answers in a bold line and up to three bullets. The page draws the tools' rows beside it as tiles, tables and
   bars. The model never retypes a table, so every figure on the page is the tool's own.

Example: "unmet demand at SFO and why" uses `traffic` (82% of seats filled, 2.1% of scheduled flights not flown),
`congestion` (33% of arrivals late against 22% US-wide, 48% of delay minutes from the air traffic system) and
`runways`. The agent then argues that SFO is held back by its airfield and airspace, not by its terminal.

| Tool | Returns |
|---|---|
| `find(query, states, hubs)` | Commercial airports by code, name, city or metro area, state or hub size, busiest first |
| `count(states)` | How many commercial airports, in the US or in given states, by hub size |
| `traffic(airports)` | Passengers, seats, load factor, flights scheduled, flown and not flown (newest 12 months) |
| `longHaul(airports, miles)` | Share of departures at or above a distance (3,000 miles unless given): all, passenger, cargo |
| `congestion(airports)` | Delay, cancellation and diversion rates, the US rate, delay minutes by cause |
| `growth(airports)` | FAA forecast boardings and operations, now and in the forecast year, yearly growth |
| `runways(airports)` | Runways, longest first: length, width, surface |
| `flightLimits(airports)` | Whether the FAA caps or manages the number of flights |
| `rank(airports, among, weights)` | The deterministic score, each KPI's rank and share, and how stable the rank is |
| `clarify(question)` | Asks the analyst back when a name is ambiguous ("which Portland?"); chat only |

The code has two features that meet only in the SQL views: `data` downloads and loads the public files, and `agent`
answers questions from the views. [Code](CODE.md) maps each concept to its class.

## Scoring methodology

The score asks one question: where would a bigger terminal be used and pay off? Five KPIs answer it, with these
default weights: **growth 30, scale 25, headroom 20, fare premium 15, load factor 10**.

Each KPI becomes a percentile among the airports the question is about. For example, New England's airports are
ranked against each other, not against Atlanta. The score is the weighted sum, from 0 to 100. Each airport also gets
its best and worst rank as the weights move, so a fragile ranking shows. Analysts can change the weights in chat; the
model passes them on but never changes the formula.

Delays count *against* an airport. Heavy air traffic delay means the runways and airspace are full, and new gates
there add no flights. The full method, its sources and a worked example: [Scoring](SCORING.md).

## Key tradeoffs

| Choice | Instead of | Why | |
|---|---|---|---|
| A few typed tools over fixed SQL | Text-to-SQL, vector search | Exact sums; refuses what it can't answer | [0001](decisions/0001-tools-over-fixed-sql.md) |
| A fixed score in Java, ranked among peers | Letting the model rank | Same input, same ranking; explainable | [0002](decisions/0002-peer-percentile-score.md) |
| Headroom counts against delay | Delay as a sign of demand | Full runways, not gates, limit most airports | [0003](decisions/0003-headroom-not-congestion.md) |
| A snapshot of bulk files, pinned by a lock | Live API calls per question | Reproducible; T-100 and the forecast have no query API | [0004](decisions/0004-pinned-snapshot-of-public-files.md) |
| DuckDB in memory, rebuilt at start | Postgres, a database file | Read-only data; fast scans of 1.5M flight rows; nothing to install | [0005](decisions/0005-in-memory-duckdb.md) |
| Java, Spring Boot and Spring AI | Python, one vendor's SDK | Swap the model by config; cheap free-tier model in development | [0006](decisions/0006-java-spring-ai-any-model.md) |
| One HTML page; cards drawn from tool rows | React, model-written tables | No front-end build; a figure can't be mistyped | [0007](decisions/0007-plain-page-cards-from-tool-rows.md) |
| Runs on the analyst's machine only | A shared web app | No login to build; the key can't be spent by others | [0008](decisions/0008-local-only.md) |
| Docker Compose as the deploy | A free cloud host | No free host fits 1 GB; no Java needed to try it | [0009](decisions/0009-docker-on-the-analysts-machine.md) |

## Where and how AI is used

| The language model (Gemini or Claude) | Deterministic code (SQL and Java) |
|---|---|
| Understanding loose questions ("LA", "strong candidate", "long haul") | Every figure, share and growth rate |
| Choosing tools and their arguments, chaining calls | The score, its percentiles and the ranks |
| Reasoning over the evidence for "why" questions | Name and code lookup |
| Follow-ups, clarifying questions, declining what is out of scope | Data download, checks and loading |
| Writing the answer with its periods, assumptions and caveats | Checking every number in the answer |

Guards: a number in the answer that no tool returned is sent back once, then cut. One question makes at most 12 tool
calls. Every tool result carries its sources' state, so the model can say what is missing. Details: [AI](AI.md).

## Assumptions, uncertainty and scope

US airports with commercial service only. Long haul means 3,000 miles or more. Unmet demand can't be observed, so
Vector reports its signs. The full list, with what each answer says when data is missing: [Scope](SCOPE.md).

## What is tested

`mvn verify` runs every source's SQL, every tool and the score on trimmed copies of the real files, with no network
and a scripted model, plus a coverage floor. The `live` tests check the real data, and the four exam questions against
figures checked by hand.
