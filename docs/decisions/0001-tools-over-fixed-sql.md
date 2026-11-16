# 0001. Typed tools over fixed SQL

**Status:** accepted, 2026-11-13

## Context

Analysts ask for exact figures: the share of long-haul flights, delay rates, a ranking of 16 airports. A wrong number
in an investment screen is worse than no number. The questions in the brief are examples; analysts will ask many
more.

## Options

| Option | For | Against |
|---|---|---|
| Text-to-SQL: the model writes queries | Answers almost anything | A plausible query can return a wrong number, and nobody sees it; hard to test |
| Retrieval over documents (vector search) | Good for prose questions | Can't add up 1.5 million flight rows |
| A semantic-layer service | Governed metrics | One more service to run, for 11 tables |
| **A few typed tools by data capability** | Each figure exact and tested; refuses what it can't do | Covers only what the tools cover |

## Decision

About ten tools grouped by what the data can tell: `find`, `count`, `traffic`, `longHaul`, `congestion`, `growth`,
`runways`, `flightLimits`, `rank`, plus `clarify`. Each runs fixed, parameterized SQL through jOOQ on the views in
`sql/views.sql`. The model chooses tools and arguments; every value from the model reaches SQL only as a bind value.

## Consequences

- An answer and a score never disagree: both read the same views.
- A new kind of question may need a new tool: one class, picked up by both the chat and the MCP server.
- A question no tool covers gets "the data doesn't cover that" rather than a guess.
