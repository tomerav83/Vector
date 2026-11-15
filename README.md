# Vector

Airport Investment Intelligence Agent: a chat agent that ranks and compares US airports for terminal-expansion
investment, from public FAA, BTS and DOT data. How it works, the scoring and its tradeoffs: [docs/DESIGN.md](docs/DESIGN.md).

## Run

Java 25 and Maven 3.9. A free Gemini key comes from [Google AI Studio](https://aistudio.google.com/apikey).

```bash
LLM_PROVIDER=google-genai GEMINI_API_KEY=... mvn spring-boot:run
```

Then open http://localhost:8080 and ask, for example:

- Which airports in New England are strong candidates for terminal expansion?
- Compare LA and Santa Ana airport congestion levels.
- What is the percentage of long haul flights out of Anchorage airport?
- What is the unmet flight demand in SFO airport and why?

Other models: `LLM_PROVIDER=anthropic ANTHROPIC_API_KEY=...`, and `LLM_MODEL` picks the model. Settings go in as
environment variables: `mvn spring-boot:run` does not pass `--` options on to the app.

Voice: the page's Speak button takes a spoken question, and the answer is read aloud (the browser's Web Speech API).

## Without an API key

`LLM_PROVIDER` defaults to `none`, so the app starts without a key.

1. `mvn spring-boot:run`. The log ends with `Data ready in … s`, and a chat question answers 503, saying how to set a
   model.
2. `curl 127.0.0.1:8081/actuator/data` lists what each source loaded; `curl 127.0.0.1:8081/actuator/health` is DOWN
   while a source is left out, and names it.
3. `mvn test -Dgroups=live -DexcludedGroups=none -Dtest=ExamQuestionsTest` checks the tools' figures for the four
   questions above against the real data.
4. Ask Claude Code instead: `SPRING_AI_MCP_SERVER_ENABLED=true mvn spring-boot:run`, then
   `claude mcp add --transport http vector http://localhost:8080/mcp`, and ask it the questions above.

## Data

- The first start downloads ~40 MB of public US aviation data (no API keys) into `data/cache/`, which takes a few
  minutes. Later starts read the cache and build the in-memory tables in ~10 s.
- `data/sources.lock.json` (committed) pins exactly which data loads, like a package lock file: per source its period,
  per file its fingerprint. A fresh clone downloads those periods; a file that changed upstream since is loaded anyway
  and flagged (`matches_lock: false`).
- No single source stops the app: one that can't be downloaded (after 2 retries) or loaded is left out, and
  `curl 127.0.0.1:8081/actuator/data` says why.
- Newer data: `curl -X POST 127.0.0.1:8081/actuator/data` while the app runs (the current data serves until the new is
  complete), or start once with `VECTOR_DATA_REFRESH=true`. Each source moves to its newest published period; one that
  fails keeps its locked period and says why. Review `git diff data/sources.lock.json`, then commit it.
- Network: DuckDB downloads its spatial extension (extensions.duckdb.org) once per machine, which the xlsx sources
  need, so a first `mvn verify` on a fresh machine is not offline.
- Both ports answer only on this machine: 8080 (the chat) and 8081 (Actuator).

## Where to look

| Concept | Where |
|---|---|
| A country's sources | `application-<cc>.yml` → `DataProperties` → `Source` |
| A source's file per period | `Source.file`, `Template` |
| The newest release | `Livecheck` |
| Download methods | `Downloader` → `impl/{Stored, OnDemand}`, picked by name in `Downloaders` |
| HTTP and retry | `Web` (`@Retryable`); `spring.http.clients.*`; `vector.data.retries/backoff` |
| File kinds, rows, fingerprints | `Formats` → `impl/{Plain, Page, TableHeader, Zip, Xlsx}`; `custom-mimetypes.xml` |
| The lock | `data/sources.lock.json`, `Lock` |
| Local copies | `Cache` |
| Build and refresh | `Snapshot`; `/actuator/data` is `DataEndpoint`; health is `DataHealthIndicator` |
| Tables and publish | `Tables`, `Script`; `sql/schema.sql`, `sql/<source>.sql`, `finish.sql` |
| Every figure | `sql/views.sql` |
| New data | `SnapshotPublished` |
| Chat and LLM | `Agent`, `agent/system.md`, `spring.ai.*` |
| Tools | `Airports`, `Traffic`, `Congestion`, `Growth`, `Runways`; codes in `AirportCodes` |
| Score | `Scores` (the tool), `Peers` (the math), `Weighting`, `Kpi`, the `candidate` view |
| Answer shape | `Evidence` |
| Rules | `ArchitectureTest` |

What you can add, and what it takes:

| To add | You write | Spring Boot does the rest |
|---|---|---|
| A source | an entry in its country's `application-<cc>.yml`, `sql/<source>.sql` (its tables in `schema.sql`) and a fixture | binds and checks the entry; a typo stops the start, naming the key |
| A country | `application-<cc>.yml` and its SQL; start with `SPRING_PROFILES_ACTIVE=us,<cc>` | profiles: each country's file adds its sources |
| A way to download, e.g. a paged API | a `@Component("name")` implementing `Downloader` in `data/source/impl`; `downloader: name` in the entry | finds the bean by name; its HTTP client takes `spring.http.clients.*` |
| A kind of file, e.g. gzip | a `@Component` implementing `Format` in `data/format/impl` | collects the beans; a container asks each entry's own format |
| A figure | a column of a view in `sql/views.sql`; the tool reading that view returns it | |
| A KPI | a `Kpi` constant and its column in the `candidate` view | `Scores`, `Peers` and `Weighting` stay as they are |
| A tool | one class in `agent/tool` implementing `ToolCallbackProvider`, with `@Tool` methods | the chat and the MCP server both pick it up |
| An LLM provider | its Spring AI starter in `pom.xml` and its block in `application.yml` | `LLM_PROVIDER` picks it |
| Work after new data | an `@EventListener` for `SnapshotPublished` | the event bus |

Adding a source, step by step:

1. Its entry in `application-us.yml`, its tables in `sql/schema.sql`, and `sql/<source>.sql`, which puts its checks
   first and reads columns by name.
2. Its fixture: start once on the fixtures. Only the new source downloads, and its lock entry is written:
   ```bash
   VECTOR_DATA_CACHE_DIR=src/test/resources/fixtures/cache \
   VECTOR_DATA_LOCK=src/test/resources/fixtures/sources.lock.json mvn spring-boot:run
   ```
   Trim the file to the rows the tests need, and put its new fingerprint in the fixture lock (`sha256sum` of a CSV;
   until then its `UsDataTest` case says `matches_lock` is false).
3. A test in `UsDataTest` named after the source, e.g. `faaRunwaysKeepTheirLengthInFeet`, and the same in
   `UsLiveDataTest`.

## Tests

- `mvn verify`: formatting, every test, the ArchUnit rules, and a coverage floor (90% of lines, 85% of branches). The
  data comes from trimmed copies of the real files (`src/test/resources/fixtures`); the chat runs against a scripted
  model, so no LLM.
- The fixtures start frozen (`vector.data.frozen`, as cargo's `--frozen`): nothing downloads, and a source the fixture
  lock lacks fails every fixture test, naming it.
- `mvn test -Dgroups=live -DexcludedGroups=none`: the real sources, and the exam's four questions against figures
  checked by hand. The `live-data` workflow (Actions → Run workflow) runs the same.
