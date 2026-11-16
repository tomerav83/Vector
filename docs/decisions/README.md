# Decisions

Each page records one choice that shapes Vector: the situation, the options weighed, what was picked, and what it
costs. They follow Michael Nygard's architecture decision record (ADR) form, so a reviewer can disagree with a
specific decision rather than with the whole design.

| # | Decision | In one line |
|---|---|---|
| [0001](0001-tools-over-fixed-sql.md) | Typed tools over fixed SQL | The model picks tools; it never writes queries |
| [0002](0002-peer-percentile-score.md) | A peer-percentile score in Java | Weighted percentiles among the airports asked about, with rank ranges |
| [0003](0003-headroom-not-congestion.md) | Delay counts against an airport | Full runways, not gates, limit most airports |
| [0004](0004-pinned-snapshot-of-public-files.md) | A pinned snapshot of public files | Bulk government files, pinned by a lock, instead of live APIs |
| [0005](0005-in-memory-duckdb.md) | DuckDB in memory | Rebuilt from the files at each start; nothing to install |
| [0006](0006-java-spring-ai-any-model.md) | Java, Spring Boot, Spring AI | Any model by config; a free model in development |
| [0007](0007-plain-page-cards-from-tool-rows.md) | One HTML page; cards from tool rows | No front-end build; the model never retypes a figure |
| [0008](0008-local-only.md) | Local only | The app answers only its own machine |
| [0009](0009-docker-on-the-analysts-machine.md) | Docker Compose as the deploy | One command, no Java; no free host fits |

A new decision takes the next number and the same headings: Status, Context, Options, Decision, Consequences.
