# 0002. A peer-percentile score in Java

**Status:** accepted, 2026-11-13; peers revised 2026-11-16

## Context

The brief asks for deterministic ranking logic, not only model output. Analysts compare what they asked about ("PVD
or BOS?", "New England"), and airports differ in size by a factor of a thousand.

## Options

| Option | Against |
|---|---|
| Let the model rank | Not reproducible; can't be audited |
| Min-max scaling over all airports | One giant airport flattens everyone else |
| Percentiles within FAA hub size | Splits a question like "New England" into separate lists |
| **Weighted percentiles among the question's airports** | A score means something only within its peer group |

## Decision

Five KPIs, each turned into a percentile among the airports the question is about (every US airport when it names
one or none), then weighted and summed to 0–100, as the OECD/JRC handbook builds composite indicators. Each airport also
gets its best and worst rank when all weights are equal and when each KPI is left out in turn. Analysts may change
the weights in chat; the formula stays in code. Details: [Scoring](../SCORING.md).

## Consequences

- Same airports and weights, same ranking: tested by hand-worked cases.
- A score compares only within one question's peers, which the answer states.
- A KPI without a value gives its weight to the others, which counts the airport at its own average on it.
