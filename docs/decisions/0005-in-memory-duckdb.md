# 0005. DuckDB in memory

**Status:** accepted, 2026-11-13

## Context

The data is read-only, rebuilt from files, and about 1.5 million flight rows. Questions scan and aggregate across all
airports. A reviewer should be able to start the app with nothing else installed.

## Options

| Option | Against |
|---|---|
| Postgres | A server to install and run, for read-only data |
| A DuckDB database file | A second copy of the data to keep in step with the files |
| Java collections | Re-implementing joins and aggregates; slow on 1.5M rows |
| **DuckDB in memory, rebuilt at each start** | About 10 s per start; about 700 MB of memory |

## Decision

An in-memory DuckDB database, loaded from the cached files at every start. DuckDB reads the CSV and xlsx files
directly in SQL. Queries go through jOOQ, so the database is config (`DB_URL`, `DB_DIALECT`), not code. `schema.sql`
turns DuckDB's external access off, so a query reaches only the cache folder.

## Consequences

- Nothing to install, and the data is always exactly what the lock says.
- jOOQ generates classes from the schema and views at each build, so a query naming a missing column fails the build.
- Writes by users (saved lists, notes) would need a real database; there are none today.
