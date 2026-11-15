# Design

Vector is a chat agent for analysts at a firm that invests in US airport terminals. It ranks and compares airports
from public FAA, BTS and DOT data. Every number it gives is computed by SQL or Java; the LLM only picks the tools and
writes the answer.

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

1. The LLM reads the question and turns names into codes with `find` ("LA" → LAX, "Logan" → BOS).
2. It calls the tools it needs. Each returns rows, the period they cover, the state of their sources and caveats.
3. For "which are strong candidates", it calls `rank`, which scores the airports in Java.
4. It answers: the answer first, then the figures, the months they cover, assumptions, caveats and how sure it is.

Example: "unmet demand at SFO and why" uses `traffic` (82% of seats filled, 2.1% of scheduled flights not flown),
`congestion` (33% of arrivals late against 22% US-wide, 48% of delay minutes from the air traffic system) and
`runways`. The agent then argues that SFO is held back by its airfield and airspace, not by its terminal.

| Tool | Class | Returns |
|---|---|---|
| `find(query, states)` | `Airports` | Commercial airports by code, name, city or state, busiest first, with hub size and metro |
| `traffic(airports)` | `Traffic` | Passengers, seats, load factor, departures scheduled vs flown, shortfall (newest 12 months) |
| `longHaul(airports, miles)` | `Traffic` | Share of departures at or above a distance (3,000 miles unless given): all, passenger, cargo |
| `congestion(airports)` | `Congestion` | Delay, cancellation and diversion rates, the US rate, delay minutes by cause |
| `growth(airports)` | `Growth` | FAA TAF boardings and operations now and in the forecast year, yearly growth |
| `runways(airports)` | `Runways` | Runways, longest first: id, length, width, surface |
| `rank(airports, weights)` | `Scores` | Deterministic score, per-KPI breakdown, rank among the hub size and its range |

Each tool class implements Spring AI's `ToolCallbackProvider`, so the chat and the MCP server both find it; a new tool
is one new class. Errors reach the page as RFC 9457 problem details: 400 for an invalid question, 502 with the
provider's reason when the model fails, 503 without a model. One question makes at most 12 tool calls
(`spring.ai.tools.limits.max-total-tool-calls`). Voice: the page takes a spoken question and reads the answer aloud.

## Where AI is used, and where it isn't

| The LLM (Gemini or Claude; any Spring AI provider, picked by `LLM_PROVIDER`) | Deterministic code (SQL and Java) |
|---|---|
| Understanding loose questions ("LA", "strong candidate", "long haul") | Every figure, share and growth rate |
| Choosing tools and their arguments, chaining calls | The score, its KPI percentiles and the ranks |
| Reasoning over the evidence for "why" questions | Name and code lookup |
| Follow-ups, clarifying questions, declining what is out of scope | Data download, checks and loading |
| Writing the answer with its periods, assumptions and caveats | |

This is tool calling over fixed, parameterized SQL, not text-to-SQL or vector search. The questions need exact sums
across every airport. Text-to-SQL "will cheerfully give you a wrong number" (dbt's 2026 benchmark), and similarity
search can't add up rows. Fixed queries are what Databricks calls trusted assets and what Google's MCP Toolbox ships.

## Data

| Source (`application-us.yml`) | What it gives | Refreshed |
|---|---|---|
| `faa-register`: FAA airport register (NASR) | Airports, codes, positions, runways | Every 28 days |
| `bts-airports`: BTS airport list | The codes BTS statistics use, matched to FAA airports | Current state only |
| `faa-boardings`: FAA enplanements | Boardings per year, hub size | Yearly |
| `faa-forecast`: FAA Terminal Area Forecast | Boardings and operations to 2055, metro areas | Yearly |
| `bts-segments`: BTS T-100 segments | Monthly passengers, seats, flights, distance per leg | Monthly, ~3 months behind |
| `bts-delays`: BTS delay causes | Monthly arrivals, delays and their causes per airport | Monthly |
| `dot-fares`: DOT airfare Table 7 | Fare premium per airport | Quarterly |

- The first start downloads ~40 MB into `data/cache/`. Every later start builds the 11 tables from the cache in
  seconds.
- `data/sources.lock.json` pins each source's period and each file's fingerprint, like a package lock file. A refresh
  (`POST 127.0.0.1:8081/actuator/data`) moves every source to its newest period while the old data keeps serving.
- A source that fails to download or load is left out, and the app still starts. Every tool result names the state of
  the sources it reads, so the agent can say what is missing.
- The views in `sql/views.sql` (`traffic`, `congestion`, `growth`, and `candidate`, one column per KPI) are the only
  place a figure the score reads is defined. The tools and the score read the same views, so an answer and a score
  never disagree.
- The two features meet only there: `agent` reads the views, and may hear `data`'s one public type, the
  `SnapshotPublished` event. `ArchitectureTest` enforces this, and keeps DuckDB and the LLM providers out of the code:
  the database and the model are config (`DB_URL`, `DB_DIALECT`, `LLM_PROVIDER`).

## Scoring

The question behind the score: where would a bigger terminal be used and pay off? That needs demand that grows,
runways and airspace with room for more flights, and passengers who pay well.

| KPI | Weight | Value | Higher is |
|---|---|---|---|
| Growth | 30 | Yearly growth of FAA-forecast boardings over the next 10 years | better |
| Scale | 25 | Boardings added in those 10 years | better |
| Headroom | 20 | NAS (air traffic system) delay minutes per arrival, newest 12 months | worse |
| Fare premium | 15 | What passengers pay above the US average for the same distance (DOT) | better |
| Load factor | 10 | Passengers per seat on scheduled flights, newest 12 months | better |

1. **Peers.** Each value becomes a percentile among the US airports of the same FAA hub size (large, medium, small,
   nonhub), a tie counting half. FAA terminal grants are awarded the same way, per airport category. Scores therefore
   compare airports of one size only, and the agent lists each size separately. An airport with no value for any
   weighted KPI is not ranked, and the answer says so; `peers` counts only ranked airports.
2. **Score.** The weighted sum of the percentiles, 0 to 100. This is how composite indicators are built (OECD/JRC
   handbook), and it is the shape of the FAA's own project ranking. Percentiles are not thrown off by outliers. Each
   KPI's `share` is its percent of the airport's score, and airports with the same score share a place (1-2-2-4,
   COINr's "sport" ranking).
3. **Missing values.** A KPI without a value is left out, and the other weights grow to fill it. This counts the airport
   at its own average on that KPI. Fare premiums exist for the lower 48 states only; small airports have no delay data.
4. **Robustness.** Each airport also gets its best and worst rank when all KPIs weigh the same and when each KPI is left
   out in turn, as the OECD handbook asks. New Haven ranks 16th of 79 small hubs, but anywhere from 3rd to 59th as the
   weights move, so the agent should not call it a sure pick.
5. **Analysts can change the weights** in chat ("weight headroom 50"). The LLM passes them to `rank`; it never changes
   the formula. Negative weights, or weights that are all zero, fail with a reason the model reads.

Why headroom and not congestion: the first plan rewarded delays as a sign of demand. The research said otherwise.
FAA's capacity study (FACT3) found that runways, not gates, limit most airports, and NAS delay is what full runways
and airspace look like. New gates at such an airport add no flights. So the score counts heavy NAS delay against an
airport, and the congestion tool still answers questions about delays.

## Key tradeoffs

| Choice | Instead of | Why |
|---|---|---|
| A few typed tools by data capability | Free text-to-SQL, a semantic-layer service | Exact numbers; refuses what it can't answer; no extra service for 11 tables |
| Snapshot of bulk files, rebuilt at startup | Calling live APIs per question | Reproducible scores; T-100 and TAF have no query API |
| In-memory DuckDB | Postgres, a DuckDB file | Read-only data rebuilt from files; fast scans of 1.5M segment rows |
| Percentiles within hub size | Min-max over all airports | A large hub and a nonhub are not peers; outliers don't flatten the scale |
| Newest 12 months for every rate | Calendar years | Recent and comparable; the period is stated in every answer |
| Chat memory keeps the last 20 messages | Storing tool results | Follow-ups work from the answer text; tools stay stateless |
| A plain HTML page, request and response | React, streaming | One file, no build; streaming can come later |

## Assumptions and scope

- US airports with commercial service only. The agent declines other countries, live flight status, and deal returns.
- Long haul means at least 3,000 statute miles nonstop, unless the user says otherwise.
- Unmet demand can't be observed. The agent reports its signs: full flights, flights scheduled but not flown, delays by
  cause, the forecast, and the runways.
- The FAA forecast is demand without capacity limits, not what an airport can serve.
- Delay data covers only the airlines that report on-time statistics (the largest US carriers).
- The newest month of T-100 can still lack late-reporting airlines.
- The score weighs demand and room to grow. It knows nothing of costs, airport finances or projects already under way.

## What is tested

`mvn verify` runs every source's SQL, every tool and the score on trimmed copies of the real files, with no network and
a scripted model, plus the ArchUnit rules and a coverage floor (90% of lines, 85% of branches). The `live` tests check
the real data, and the exam's four questions against figures checked by hand.

## Limits and next steps

- A fresh clone can't reproduce a file that grows (the BTS year files) or one published only as its current state
  (the BTS list). It loads them and flags the difference (`matches_lock: false`).
- The first start needs extensions.duckdb.org for DuckDB's spatial extension, which reads the xlsx sources.
- The lock keeps the entry of a removed source until it is deleted by hand.
- A refresh request stays open until done: about 4 minutes, up to ~8 when every BTS year changed. A second refresh
  waits for the first. While it runs, memory holds two snapshots (~820 MB after a start, ~1 GB at the end).
- Source names are global: if two countries name a source the same, the later profile replaces the earlier one's
  entry. Names start with the publisher (`faa-`, `bts-`) to avoid it.
- Naming any profile replaces the default `us`, so name it too (`SPRING_PROFILES_ACTIVE=us,dev`). A start with no
  country fails with `No sources: start with a country's profile`.
- Chat memory is in memory and keyed by conversation, not by user: fine for one analyst on one machine.
- Palm Beach (DJT) has no forecast: TAF 2025 still files it under its old code, PBI.
- Known caps the data lacks (Santa Ana's passenger limit and curfew, slot-controlled airports) are left to the analyst.
- Runway spacing isn't in the register, so the agent can't tell whether parallel runways work in bad weather.
- Next: live API tools (NAS status, weather, the newest months from the BTS and DOT data APIs), each an
  `@HttpExchange` client and one tool class; show each tool call in the page; funding data (NPIAS, AIP, ATP, PFC); a
  peak-hour demand KPI (FAA terminal planning sizes terminals for the peak hour of an average day in the peak month);
  and an evaluation set of questions run against the real model on demand.
