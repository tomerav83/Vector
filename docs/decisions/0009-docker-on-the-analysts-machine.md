# 0009. Docker Compose as the deploy

**Status:** accepted, 2026-11-16

## Context

Before this, running Vector needed Java 25 and Maven. A reviewer should try it with one command. The app needs about
700 MB of memory with its data loaded and more during a refresh, a model key, and to stay private ([0008](0008-local-only.md)).

## Options (free tiers checked November 2026)

| Option | Against |
|---|---|
| Render free | 512 MB of memory; sleeps after 15 minutes |
| Railway | A trial credit, then 0.5 GB per service |
| Fly.io | No free tier for new accounts |
| Hugging Face Docker Spaces | 16 GB free hardware, but creating a Docker Space now needs a paid plan |
| Oracle Cloud free VM | Enough memory, but a VM to set up, patch and secure |
| Google Cloud Run | Needs a billing account; a cold start re-downloads the data |
| GitHub Codespaces | The browser's requests come from another origin (`*.app.github.dev`), which `HostFilter` refuses |
| **Docker Compose on the analyst's machine** | Needs Docker; no shared URL |

Any public URL would also need a login, since whoever reaches it spends the key.

## Decision

A two-stage `Dockerfile` (Maven builds the jar; a Java 25 runtime image runs it as a non-root user) and a
`docker-compose.yml` that publishes both ports on 127.0.0.1, keeps the downloads in a volume, and reads the model key from
a gitignored `.env`. For Java users, the Maven wrapper (`./mvnw`) removes the need to install Maven.

Measured on the first run: the first start downloads the data in about 3 minutes; restarts with the volume take about
12 seconds; the container holds about 700 MB.

## Consequences

- `docker compose up` is the whole setup ([Setup](../SETUP.md)).
- The same image can run on any paid container host once a login and an allowed-host setting are added.
