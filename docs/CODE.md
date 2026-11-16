# Code

How the code is laid out, how to extend it, and how to test it. Java formatting is checked by Spotless, SQL layout by
SQLFluff (`.sqlfluff`).

## Layout

Two features, each a package under `com.vector.airport`, that meet only in the SQL views (`sql/views.sql`) and one
event (`SnapshotPublished`):

- `data`: downloads the public files, checks them against the lock, and loads them into DuckDB.
- `agent`: the chat, its tools over the views, and the score.

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
| Every figure | `sql/views.sql`; those with a parameter in their tool's jOOQ query (`Traffic.longHaul`, `Airports.count`) |
| Chat and model | `Agent`, `agent/system.md`, `spring.ai.*` |
| Tools | `Airports`, `Traffic`, `Congestion`, `Growth`, `Runways`; codes in `AirportCodes`; `Clarification` |
| Number check | `Grounding`, `GroundingAdvisor` |
| Score | `Scores` (the tool), `Peers` (the math), `Weighting`, `Kpi`, the `candidate` view |
| Answer shape | `Evidence` (each tool's rows), `Answer`, `Call` |
| Local-only check | `HostFilter` |
| The page | `src/main/resources/static/index.html` |

## What you can add

| To add | You write | Spring Boot does the rest |
|---|---|---|
| A source | an entry in its country's `application-<cc>.yml`, `sql/<source>.sql` (its tables in `schema.sql`) and a fixture | binds and checks the entry; a typo stops the start, naming the key |
| A country | `application-<cc>.yml` and its SQL; start with `SPRING_PROFILES_ACTIVE=us,<cc>` | profiles: each country's file adds its sources |
| A way to download, e.g. a paged API | a `@Component("name")` implementing `Downloader`; `downloader: name` in the entry | finds the bean by name; its HTTP client takes `spring.http.clients.*` |
| A kind of file, e.g. gzip | a `@Component` implementing `Format` | collects the beans; a container asks each entry's own format |
| A figure | a column of a view in `sql/views.sql`; the tool reading that view returns it | |
| A KPI | a `Kpi` constant and its column in the `candidate` view | `Scores`, `Peers` and `Weighting` stay as they are |
| A tool | one class implementing `ToolCallbackProvider`, with `@Tool` methods | the chat and the MCP server both pick it up |
| A model provider | its Spring AI starter in `pom.xml` and its block in `application.yml` | `LLM_PROVIDER` picks it |
| Work after new data | an `@EventListener` for `SnapshotPublished` | the event bus |

### Adding a source, step by step

1. Its entry in `application-us.yml`, its tables in `sql/schema.sql`, and `sql/<source>.sql`, which puts its checks
   first and reads columns by name.
2. Its fixture: start once on the fixtures. Only the new source downloads, and its lock entry is written:
   ```bash
   VECTOR_DATA_CACHE_DIR=src/test/resources/fixtures/cache \
   VECTOR_DATA_LOCK=src/test/resources/fixtures/sources.lock.json ./mvnw spring-boot:run
   ```
   Trim the file to the rows the tests need, and put its new fingerprint in the fixture lock (`sha256sum` of a CSV;
   until then its `UsDataTest` case says `matches_lock` is false).
3. A test in `UsDataTest` named after the source, e.g. `faaRunwaysKeepTheirLengthInFeet`, and the same in
   `UsLiveDataTest`.

## Tests

- `./mvnw verify`: formatting (Spotless, Palantir style), every test, and a coverage floor (90% of lines, 85% of
  branches). The data comes from trimmed copies of the real files (`src/test/resources/fixtures`); the chat runs on
  `ScriptedModel`, so no model and no network.
- `./mvnw spotless:apply` fixes formatting.
- Each build generates jOOQ's classes of the tables and views (package `view`) from `schema.sql` and `views.sql`, so a
  query naming a missing column fails the compile. After a clone, run `./mvnw generate-sources` once for the IDE.
- The fixtures start frozen (`vector.data.frozen`, like Cargo's `--frozen`): nothing downloads, and a source the
  fixture lock lacks fails every fixture test, naming it.
- `./mvnw test -Dgroups=live -DexcludedGroups=none`: the real sources, and the four exam questions against figures
  checked by hand. The `live-data` workflow (Actions → Run workflow) runs the same.
- CI (`.github/workflows/ci.yml`) runs `verify` and lints the SQL:
  `pipx run --spec sqlfluff==4.5.0 sqlfluff lint src/main/resources/sql`.
