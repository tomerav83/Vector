# Vector

![Vector's mascot, a small yellow robot plane, over the app's world map of flight routes](docs/images/mascot.jpg)

**Vector helps an investment analyst find US airports where a bigger terminal would pay off.** Ask it in plain
English. It reads public FAA, BTS and DOT data, ranks and compares airports with a fixed scoring method, and says why,
how sure it is, and what the data can't tell.

> Which airports in New England are strong candidates for terminal expansion?
> Compare LA and Santa Ana airport congestion levels.
> What is the percentage of long haul flights out of Anchorage airport?
> What is the unmet flight demand in SFO airport and why?

Follow-ups work too ("and the three largest in Maine?", "weight growth higher"). You can speak a question, and Vector
reads the answer aloud.

## Why it exists

The firm invests in airport modernization. Analysts spend days pulling traffic, delay and forecast tables before they
can say which airports deserve a closer look. Vector does that first pass in a minute. It is a research assistant, not
a decision maker: every figure links to its public source and period, and the ranking method is written down and fixed.

## What makes it trustworthy

- **The numbers come from code, not the model.** SQL and Java compute every figure and the score. The language model
  picks which data to fetch and writes the explanation. If it writes a number that no tool returned, Vector asks it to
  fix the answer, and cuts the number if it still appears.
- **A fixed, explainable score.** Five measures (growth, scale, airfield headroom, fare premium, load factor), each
  turned into a rank among the airports you asked about. See [how the score works](docs/SCORING.md).
- **Honest about its limits.** Each answer gives the months it covers. It also says which source is missing and what
  it had to assume. See [assumptions and scope](docs/SCOPE.md).
- **Public data, pinned.** About 40 MB of government files, downloaded on the first start and pinned by a lock file,
  like a package lock, so two people get the same answers. See [the data](docs/DATA.md).

## Try it

The quickest way needs only [Docker](https://docs.docker.com/get-docker/) and a free
[Gemini API key](https://aistudio.google.com/apikey):

```bash
git clone https://github.com/tomerav83/Vector.git && cd Vector
printf 'LLM_PROVIDER=google-genai\nGEMINI_API_KEY=your-key\n' > .env
docker compose up
```

Open http://localhost:8080 once the log says `Data ready`. The first start downloads the data, which takes a few
minutes. Later starts take about 10 seconds.

With Java 25 instead of Docker, or without any key, or to plug in Claude Code: the [setup guide](docs/SETUP.md).

## Documentation

| Read this | To learn |
|---|---|
| [Setup guide](docs/SETUP.md) | Every way to run it, the settings, and fixes for common problems |
| [Design](docs/DESIGN.md) | The architecture on one page: how a question becomes an answer |
| [Scoring](docs/SCORING.md) | The KPIs, their weights and why, with a worked example |
| [Where AI is used](docs/AI.md) | What the model does, what it is never trusted with, and the guards |
| [Data](docs/DATA.md) | Each public source, what it gives, how fresh it is |
| [Assumptions and scope](docs/SCOPE.md) | What Vector assumes, where it is unsure, what it won't answer |
| [Decisions](docs/decisions/README.md) | The main choices, one page each: the options weighed and why this one |
| [Code](docs/CODE.md) | Where things live in the code; how to add a source, a KPI, a tool or a model |

## Built with

Java 25, Spring Boot 4.1, Spring AI 2.0 (Gemini or Claude, swappable), DuckDB in memory, jOOQ, and a plain HTML page.
`mvn verify` tests every source, tool and the score on trimmed copies of the real files, with no network and no
model.

## License

Copyright © 2026 Tomer Aviram. All rights reserved. Shared for hiring evaluation only; see [LICENSE](LICENSE).
