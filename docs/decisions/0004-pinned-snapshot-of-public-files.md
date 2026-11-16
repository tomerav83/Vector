# 0004. A pinned snapshot of public files

**Status:** accepted, 2026-11-13; refresh added 2026-11-14

## Context

The brief asks for public APIs. The data that matters most has no query API: BTS T-100 traffic and the FAA forecast
are published as bulk files. Scores must be reproducible: two analysts asking the same question should get the same
ranking.

## Options

| Option | Against |
|---|---|
| Call live APIs per question | T-100 and TAF have none; answers would change between two askings |
| Commit the data to git | 40 MB of others' files in the repo; stale by design |
| A data mirror built by CI | GitHub's terms don't allow Actions as a data mirror or scraper |
| **Download the files at first start, pinned by a lock** | A first start takes a few minutes |

## Decision

The app downloads each source's public file (FAA, BTS and DOT sites, the DOT open-data API) into a local cache. The
committed `data/sources.lock.json` pins each source's period and each file's fingerprint, as npm's or Cargo's lock
pins packages. A refresh, on demand, moves each source to its newest period and rewrites the lock for review.

## Consequences

- A fresh clone gets the same periods, and a file changed upstream is flagged, not hidden.
- A broken government site leaves one source out; the app still starts, and answers say what is missing.
- No live data yet (today's delays, weather). Live tools are a next step, and would never feed the score.
