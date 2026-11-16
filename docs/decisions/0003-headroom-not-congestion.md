# 0003. Delay counts against an airport

**Status:** accepted, 2026-11-15; replaced "congestion as demand"

## Context

The first plan rewarded delays as a sign of demand that a bigger terminal could serve. The research said otherwise.
The FAA's capacity study (FACT3) found that runways, not gates, limit most airports. Delay caused by the air traffic
system (NAS delay) is what full runways and airspace look like. New gates at such an airport add no flights.

## Options

| Option | Against |
|---|---|
| Delay as demand (higher is better) | Rewards airports where a terminal can't add flights |
| Leave delay out of the score | Hides the main reason an expansion fails to pay |
| **Headroom: NAS delay minutes per arrival, lower is better** | Penalizes busy airports that do have terminal problems |

## Decision

The score's headroom KPI (weight 20) counts NAS delay per arrival against an airport. The `congestion` tool still
answers delay questions in full, and the `flightLimits` tool says where the FAA caps or manages flights.

## Consequences

- For "unmet demand" questions, the agent tells airfield limits (high NAS delay, capped flights) from terminal
  limits (full flights, low NAS delay). SFO reads as airfield-bound.
- Only delay causes the airlines report are used. Small airports have none, so they are scored on the other KPIs.
