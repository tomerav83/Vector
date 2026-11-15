# Java code style

Taken from code the owner wrote or approved (`data` and its sub-packages). Follow it before showing any
code; the owner should not have to review names or structure.

## Names

Names are terms, not descriptions: one or two words a Java developer already knows. Needing four words means the
method does too much or sits in the wrong class.

| Bad (a description)          | Good (a term)  |
|------------------------------|----------------|
| `newestPublishedPeriodAfter` | `newest`       |
| `failOnBadSpec`              | `validate`     |
| `holdsData`, `hasDataRow`    | `hasRows`      |
| `downloaderFor`              | `forName`      |

- **Classes:** a domain noun: `Snapshot`, `Source`, `Cache`, `Tables`, `Lock`, `Template`. A registry is the
  plural (`Downloaders`, `Formats`). No role suffix: `Manager`, `Helper`, `Util`, `Processor`, `Handler`,
  `Service`, `Renderer`, `Probe` (each fails `ArchitectureTest`). A framework contract keeps the framework's suffix:
  `*Properties`, `*Configuration`, `*Endpoint`, `*HealthIndicator` (`DataProperties`, `DataHealthIndicator`).
- **Methods** use the JDK prefixes ([Joda list](https://blog.joda.org/2011/08/common-java-method-names.html)):

  | Prefix | Meaning | Here |
  |---|---|---|
  | none (noun) | accessor, no `get` | `periods(web)`, `file(period)`, `key()` |
  | `is` / `has` | boolean | `isOffered(page, field)`, `hasRows(file, formats)` |
  | `find` | lookup that does work, returns `Optional` | `lock.find(source)`, `formats.find(file)` |
  | `validate` | throws when wrong, returns nothing | `downloaders.validate(sources)` |
  | `create`, `fromX` | static factory | `Tables.create(db, ...)` |
  | domain verb | the codebase's own words | `render`, `fetch`, `download`, `extract`, `load`, `publish` |

- **Vocabulary:** reuse the words already in the code and in `application-<cc>.yml`: `render`, `context`, `period`,
  `source`, `file`, `download`, `fetch`, `lock`, `publish`. Never add a synonym, and never give one name two meanings
  (the old `file(values)` vs `files(period)`). Overloading is only for the same meaning on another input type
  (`Template.render(template, context)` / `render(template, resolver)`). One concept keeps one name from
  `application-<cc>.yml` through the SQL to the Java (`bts-airports` → `sql/bts-airports.sql` → `bts_airport`).
- **Parameters** name the role, not the type: `locked`, `target`, `context`.
- Never `all`, `of`, `get` or `data` on a new factory or method.

## Breaking up a class

1. **Extract a class only when some fields are used only by some methods**
   ([Extract Class](https://refactoring.com/catalog/extractClass.html)), or when the methods are independent features
   of one kind that each grow on their own: each family of agent tools is its own class (`Traffic`, `Congestion`).
   Example: the cache folder, the downloaders and the formats were used only by fetching files, so they became
   `Cache`.
2. **Each concept has one owner; find it before writing code.** Grep the package for the class that already owns the
   concept and extend it. Never re-implement part of it next to the caller. Example: a file's kind and whether it holds
   rows belong to `Formats` and its `Format` beans (`Formats.detect`, `Zip.hasRows`, `Plain.hasRows`), so `Cache` only
   calls `formats.detect(staged).hasRows(staged, formats)`.
3. **One step of one algorithm stays a private method.** `Snapshot.validate(lock)` is a step of the build, so it is
   not a class. A class with one method and one caller is not a breakdown, unless it implements an interface the
   framework or the codebase calls (`HealthIndicator`, `Downloader`), or holds a whole algorithm over its own data
   (`Peers`).
4. **The original class reads as the summary:** the port/public methods first, then private helpers in call order.
5. **No null as "no value" in parameters:** pass a `Predicate` or an `Optional` result instead (`load(..., locked,
   refresh)`, `locked` being `lock.find(name)`). Two exceptions: an optional `@Tool` parameter, which Spring AI passes
   as null (the tool replaces it on its first line), and a JSON record field for a value the data lacks.

## Packages

- Package by feature: `agent` and `data`, each with a sub-package per family it uses (`agent.tool`, `data.source`,
  `data.format`). A new class goes into the package whose concept it is (rule 2 above), not where its first caller
  happens to be.
- **The features meet only in the SQL views (`sql/views.sql`) and data's events (`SnapshotPublished`).** Each
  feature's beans are made in its `<Feature>Configuration` (`DataConfiguration`). An agent tool is a class in
  `agent.tool` that implements `ToolCallbackProvider`; the chat and the MCP server take every one.
- **No nested types.** Each class, interface, enum and record goes in its own file, package-private unless another
  package uses it. A main type's helpers sit beside it in its package. When they would crowd a package that holds
  other things, they get a sub-package named after the main type:
  - `data/format/{Format, Formats}` + `data/format/impl/{Plain, Page, TableHeader, Zip, Xlsx, XlsxDetector, Sha256}`,
    not `Format.Zip` inside `Format.java`. Implementations of a package's interface go in its `impl` sub-package, like
    `data/source/impl/{Stored, OnDemand}` for `Downloader`.
  - `data/source/{Source, SourceFile, Form, Template, Livecheck, Downloader, Downloaders, Web, Unpublished}`: a source
    and how its files download.
  - `data/{Snapshot, DataEndpoint, DataHealthIndicator, DataConfiguration, DataProperties, SnapshotPublished, Cache,
    Tables, Script, Lock, LockedSource, LockedFile, Version, Fetched}`: the package is Snapshot's own, so its build
    steps stay package-private.
  - `agent/{Agent, Question, Answer}` + `agent/tool/{Airports, Traffic, Congestion, Growth, Runways, AirportCodes,
    Evidence}` and the score's `{Scores, Peers, Weighting, Candidate, Kpi, Score, Contribution, Weight, Hub}`.
  - `data.source` and `data.format` never import `data`; `ArchitectureTest` fails on a package cycle.
- **A type that leaves its parent takes a full name.** `Lock.Source` becomes `LockedSource`, and `Source.File`
  becomes `SourceFile`.
- **An extracted class gets methods, not field access.** `plan.load(tx)`, not
  `plan.country.load(tx, plan.periods(), plan.files(), plan.skip)`.
- Package-private by default; `public` only when another package calls it.
- No `exception` or `util` packages. An exception gets its own class only when a caller or a contract needs its type
  (`Unpublished`); otherwise it is a JDK exception with a message
  ([Effective Java](https://www.oreilly.com/library/view/effective-java-3rd/9780134686097/) Item 72).

## SQL and config

- Boot binds each yml key once. A record that holds the keys in another shape takes a `@ConstructorBinding`
  constructor, and what differs per use is rendered from it, never bound again (`Source.file(period)`).
- `sql/schema.sql` holds the tables, `sql/<source>.sql` fills a source's tables (its checks first, columns read by
  name, codes mapped with `case … else error()`), and `sql/views.sql` holds every figure a reader sees.
- A figure is a view column, never Java arithmetic; the tools and the score read the same views.

## Tests

- Tests sit in the same package as the class (`Zip` → `data/format/impl/ZipTest`).
- A test is named by its behaviour, starting with the scenario or the source key (`btsDelaysKeepMissingValuesAsNull`).
- Example lists are `@ParameterizedTest` tables with a reason column.
- `@SpringBootTest` comes only through `@FixtureDataTest`, `@LiveDataTest` or `AppTest`. No mocks: fakes
  (`FakeSite`, `ScriptedModel`).

## Code shape

- Keep the idiomatic shape: stream pipeline + small helper. No loop + `ArrayList` for a stream's job.
- `Optional.ofNullable(map.get(k)).orElseThrow(() -> ...)`, never `if (x == null) throw`.
- Never declare a variable before `try` just to assign it inside; return from the try or use a helper.
- No magic numbers: columns by header name, codes as enums, constants named.
- No one-use constants, and no comments that repeat the code. A literal passed to a named parameter or annotation
  attribute is already named.
- Javadoc starts with one sentence: what it is, an example, what fails. A class whose callers must know an order or a
  contract adds one paragraph.
- Records for value-like classes, validated in their compact constructor (`Question`); `final class` + private
  constructor for static-only ones (`Template`).

## Before showing code

Go through the diff against every rule above, then run `mvn -q spotless:apply && mvn -q verify`. Show the final code,
not candidate names to pick from.
