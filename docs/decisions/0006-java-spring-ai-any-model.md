# 0006. Java, Spring Boot and Spring AI

**Status:** accepted, 2026-11-13

## Context

The app should be easy to move to the client's model provider, and cheap to build: the model is called many times
during development. The stack was set to Java and Spring Boot from the start.

## Options

| Option | Against |
|---|---|
| Python with one vendor's SDK | Locks the code to one provider |
| An agent framework (LangGraph and others) | A second language and runtime for this team |
| **Spring Boot with Spring AI's `ChatClient`** | Spring AI is younger than the Python frameworks |

## Decision

Java 25 and Spring Boot 4.1, with Spring AI 2.0. `LLM_PROVIDER` picks the model at start: Gemini Flash-Lite on the
free tier for development, Claude Haiku as the second provider, or none. A new provider is its Spring AI starter and
a config block. The tools are also served as an MCP server, so Claude Code can use them without a key in the app.

## Consequences

- No code change to switch models, and development costs nothing on Gemini's free tier.
- Spring Boot carries the extension points: tools, data sources, download methods and file formats are beans.
- Tests run on a scripted model, so `mvn verify` needs no key and no network.
