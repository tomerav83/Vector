# 0007. One HTML page; cards drawn from the tools' rows

**Status:** accepted, 2026-11-16

## Context

The brief asks for a chat interface (voice a bonus) and puts clarity over polish. Analysts read tables of figures,
and a model that retypes a table can mistype it.

## Options

| Option | Against |
|---|---|
| React with a build step | A second toolchain (Node) for one page |
| The model writes markdown tables | Figures retyped by the model; long answers |
| **One static HTML page; the page draws each tool's rows** | No streaming yet |

## Decision

`src/main/resources/static/index.html`, served by Spring Boot. The model's answer is a bold line and up to three
bullets. Beside it travel the tool results, and the page draws them by fixed rules: tiles for one airport, a table
for several, a head-to-head table for a ranking, and a bar for delay causes. The browser's Web Speech API takes
spoken questions and reads answers aloud. The mascot shows the turn's state: searching, answered, asking back, or
failed.

## Consequences

- Every figure on the page is the tool's own; the model's text is the conclusion.
- No front-end build, and `mvn` alone builds everything.
- Answers arrive whole, not streamed. Streaming can come later.
