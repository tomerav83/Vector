# Where AI is used

Vector uses a language model for language: understanding the question, choosing what data to fetch, and explaining
the result. It never trusts the model with a number. Every figure and the score come from SQL and Java, which give the
same answer every time.

## What the model does, and what code does

| The language model | Deterministic code (SQL and Java) |
|---|---|
| Understanding loose questions: "LA", "strong candidate", "long haul" | Every figure, share and growth rate (`sql/views.sql`) |
| Turning names into airport codes, through the `find` tool | The lookup behind `find`: codes, names, cities, metro areas |
| Choosing tools and their arguments, chaining several calls | The score, its percentiles, ranks and rank ranges (`Peers`) |
| Reasoning over the evidence for "why" questions | Downloading, checking and loading the public data |
| Follow-ups, asking back when a name is ambiguous, declining what is out of scope | Checking that each number in the answer came from a tool (`Grounding`) |
| Writing the answer, with its periods, assumptions and how sure it is | Drawing the tables and tiles on the page from the tools' rows |

## How a turn runs

1. The page posts the question and a conversation id to `POST /api/chat`.
2. `Agent` sends it to the model through Spring AI's `ChatClient`, with the system prompt
   (`src/main/resources/agent/system.md`), the last 20 messages of the conversation, and the tools.
3. The model calls tools, often several in a row. Each tool runs fixed, parameterized SQL and returns rows plus their
   period, the state of their sources, definitions and limitations.
4. The model writes a short answer: a bold first line and up to three bullets.
5. The grounding check runs (below). The page shows the answer, and beside it each tool's rows as cards.

## Why tools over fixed SQL

The questions need exact sums across every airport ("percentage of long-haul flights", "rank New England"). Three
common designs were weighed:

- **Text-to-SQL** lets the model write queries. It handles any question, but it can write a plausible query that
  returns a wrong number, and nobody notices.
- **Retrieval (vector search over documents)** finds similar text. It can't add up 1.5 million flight rows.
- **Typed tools over fixed queries** (chosen) can answer only what the tools cover, but each figure is right by
  construction and tested. A question outside the tools gets "the data doesn't cover that", not a guess.

More in [decision 0001](decisions/0001-tools-over-fixed-sql.md).

## The guards

| Risk | Guard |
|---|---|
| The model invents or misremembers a number | `GroundingAdvisor` checks every number in the text against this turn's tool results, the question and the fixed wording, within rounding (81% matches 0.8095). A stray number is sent back to the model once, naming it. If it is still there, the sentence is cut and logged. |
| The model retypes a table wrong | It doesn't type tables: the page draws them from the tools' JSON |
| A loose name picks the wrong airport | The model names its pick ("LA: I used LAX"). When nothing points to one and the picks differ (Portland, Maine or Oregon), it calls `clarify`, which asks the analyst and ends the turn |
| A runaway loop of tool calls | At most 12 tool calls per question (`spring.ai.tools.limits.max-total-tool-calls`) |
| A tool fails | The error reaches the model as JSON it can read and fix, e.g. by looking the code up first |
| Data is missing | Every tool result names its sources' state, so the answer says what is missing |
| The model changes the score | It can only pass weights, which `Weighting` checks; the formula is code |
| Out-of-scope questions | The prompt lists what is covered; airports abroad, deal returns and live flight status get "not covered" and what the data can show instead |

## Which model

Any model Spring AI supports. `LLM_PROVIDER` picks one at start; no code changes.

- **Gemini Flash-Lite** (`google-genai`) is the default for development: the free tier is enough to build and demo.
- **Claude Haiku** (`anthropic`) is the second provider, wired in by config. The tests don't call it, since they call
  no model.
- **None**: the app starts without a key, and its tools can serve Claude Code or any MCP client.

The tools are plain data and the prompt asks for short answers, so a small, cheap model does the job. A larger model
mostly improves the "why" reasoning.

## How it is tested without a model

`mvn verify` runs the chat against `ScriptedModel`, a fake that replays tool calls and answers. The tools, the
grounding check and the page's data are tested with no network and no key. The four exam questions are checked against
the real data in the `live` tests (`ExamQuestionsTest`), at the tool level, where the figures are deterministic.
