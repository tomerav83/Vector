# Setup guide

Vector runs on your own machine. There are two ways to start it: with Docker (no Java needed) or with Java 25. Both
serve the chat on http://localhost:8080. Both answer only this machine, so nobody else on the network can spend your
API key.

## 1. Get a model key

Vector needs a language model to chat. The cheapest is Google's Gemini free tier: create a key at
[Google AI Studio](https://aistudio.google.com/apikey). An Anthropic key works too. Without any key the app still
starts: see [Without a key](#without-a-key).

## 2a. Run with Docker (easiest)

You need [Docker Desktop](https://docs.docker.com/get-docker/) or Docker Engine with Compose.

```bash
git clone https://github.com/tomerav83/Vector.git && cd Vector
printf 'LLM_PROVIDER=google-genai\nGEMINI_API_KEY=your-key\n' > .env
docker compose up
```

What happens:

1. Docker builds the app (a few minutes the first time).
2. The app downloads about 40 MB of public data into a Docker volume. This takes about 3 minutes, once.
3. The log prints `Data ready in … s`. Open http://localhost:8080.

Later, `docker compose up` starts in about 10 seconds, because the volume keeps the downloads. To stop, press Ctrl+C.
`docker compose down -v` also deletes the downloads. After you change the code, run `docker compose up --build`.

`.env` is gitignored, so the key never lands in a commit. Change the model there and run `docker compose up` again.

## 2b. Run with Java

You need Java 25, for example [Eclipse Temurin](https://adoptium.net/). Maven is optional: the repo's `./mvnw`
downloads the right Maven.

```bash
git clone https://github.com/tomerav83/Vector.git && cd Vector
LLM_PROVIDER=google-genai GEMINI_API_KEY=your-key ./mvnw spring-boot:run
```

On Windows without WSL use `mvnw.cmd`, and set the variables first with `set LLM_PROVIDER=google-genai`.

The first start downloads the data into `data/cache/` (a few minutes). Later starts read that folder and are ready in
about 10 seconds.

## Settings

All settings are environment variables. Put them in `.env` with Docker, or before the command with Java.
`mvn spring-boot:run` does not pass `--` options to the app, so use variables.

| Variable | Default | What it does |
|---|---|---|
| `LLM_PROVIDER` | `none` | `google-genai`, `anthropic` or `none` |
| `GEMINI_API_KEY` | | Key for `google-genai` |
| `ANTHROPIC_API_KEY` | | Key for `anthropic` |
| `LLM_MODEL` | `gemini-3.5-flash-lite` (Gemini) or `claude-haiku-5-5` (Anthropic) | Picks another model of the same provider |
| `VECTOR_DATA_REFRESH` | `false` | `true` loads the newest published data at start and updates the lock file |
| `SPRING_AI_MCP_SERVER_ENABLED` | `false` | `true` also serves the tools to MCP clients at `/mcp` |

The rest lives in `src/main/resources/application.yml`, where each setting has a comment.

## Without a key

`LLM_PROVIDER` defaults to `none`, so a fresh clone starts without a key. The chat answers that no model is set and
says how to set one. Everything else works:

- `curl 127.0.0.1:8081/actuator/data` lists what each data source loaded.
- `curl 127.0.0.1:8081/actuator/health` is `UP`, or `DOWN` with the name of a source that failed.
- `./mvnw test -Dgroups=live -DexcludedGroups=none -Dtest=ExamQuestionsTest` checks the figures behind the four exam
  questions against the real data.

## Use it from Claude Code (MCP)

Vector's tools can also serve any MCP client, with no model key in Vector:

```bash
SPRING_AI_MCP_SERVER_ENABLED=true ./mvnw spring-boot:run
claude mcp add --transport http vector http://localhost:8080/mcp
```

Then ask Claude Code the same questions. It calls Vector's tools; Vector computes the figures. With Docker, add
`SPRING_AI_MCP_SERVER_ENABLED=true` to `.env`.

## Keep the data current

The lock file `data/sources.lock.json` pins which month or year of each source loads, so every clone gives the same
answers. To move to the newest data while the app runs:

```bash
curl -X POST 127.0.0.1:8081/actuator/data
```

The current data keeps serving until the new data is complete (about 4 minutes). A source that fails keeps its old
period, and the reply says why. Review `git diff data/sources.lock.json`, then commit it. To refresh at start
instead, set `VECTOR_DATA_REFRESH=true` for one start.

## Run the tests

```bash
./mvnw verify
```

This checks formatting, runs every test on small copies of the real files with a scripted model (no network, no key),
and enforces a coverage floor of 90% of lines and 85% of branches. The real sources:
`./mvnw test -Dgroups=live -DexcludedGroups=none`.

## Troubleshooting

| You see | Why | Fix |
|---|---|---|
| The chat says no model is set (503) | `LLM_PROVIDER` is `none` | Set it and its key; restart |
| 502 with the provider's message | The key is wrong, or the free tier's per-minute limit was hit | Check the key; wait a minute |
| `403 Vector answers only pages and clients on this machine` | You opened it by another name than `localhost` or `127.0.0.1` | Use http://localhost:8080 |
| `health` is `DOWN`, naming a source | That government site failed after 2 retries; the app runs without it | `curl -X POST 127.0.0.1:8081/actuator/data` later, or restart |
| The first start or `mvn verify` fails offline | DuckDB downloads its spatial extension once per machine (extensions.duckdb.org) | Connect once; it is cached in `~/.duckdb` |
| `Port 8080 is already in use` | Another app holds the port | Stop it, or set `SERVER_PORT=8090` (Docker: change the port mapping in `docker-compose.yml`) |
| Docker: the first start is slow | It is downloading the data | Wait for `Data ready`; the volume keeps it |

## Why there is no public URL

The easiest deploy that keeps the app private is Docker on your own machine. A shared, hosted copy would need two
more things first:

- **A login.** Anyone who reaches a public URL spends the model key. The app also refuses every host but this
  machine (`HostFilter`), because MCP's spec asks local servers to do so.
- **About 1 GB of memory.** The app holds about 700 MB once the data loads, and more during a refresh. In November
  2026 the free tiers don't fit:
  Render and Railway give 512 MB, Fly.io has no free tier for new accounts, and Hugging Face now needs a paid plan for
  Docker Spaces.

The same image runs on any paid container host (Cloud Run, Render, a VM) once a login is added. See
[decision 0009](decisions/0009-docker-on-the-analysts-machine.md).
