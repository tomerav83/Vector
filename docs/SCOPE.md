# Assumptions, uncertainty and scope

The brief asks the agent to be clear about what it assumes, how sure it is and what it covers. This page lists all
three. The agent says the same things in its answers, where they apply.

## What Vector covers

- **US airports with commercial airline service**, about 500 of them: those in the FAA's boardings list.
- **Public federal data**: traffic, delays, forecasts, runways, fares and federal flight limits (see [Data](DATA.md)).
- **Questions that the tools answer**: find, count, traffic, long haul, congestion, growth, runways, flight limits
  and the investment score. Follow-ups keep the place, airports and assumptions of earlier answers.

## What it declines

| Question | Answer |
|---|---|
| Airports outside the US | Not covered; it offers what it can show, e.g. US airports with flights to Europe |
| A deal's returns, costs or financing | Not covered: no airport finances in the data |
| Live flight status, today's delays | Not covered: the data is monthly |

## Assumptions

| Assumption | Why | How to change it |
|---|---|---|
| "Long haul" means 3,000 miles or more nonstop | Longer than any nonstop within the lower 48 states | Say another distance; the tool takes it |
| "Recent" means the newest 12 months | Comparable across airports and seasons | Fixed; each answer states the months |
| "LA" is LAX, "Santa Ana" is SNA, and so on | The busiest match is the likely one | The answer names its pick; say another |
| Peers are the airports the question names, or its region | Answers compare what was asked | Name the airports to compare |
| The default weights (growth 30, scale 25, headroom 20, fare premium 15, load factor 10) | Demand pays for a terminal; full runways block it ([Scoring](SCORING.md)) | Ask for other weights in chat |
| An airport the FAA doesn't list has no federal flight limit | Federal limits exist only by FAA rule or order | |

## Uncertainty, and how answers show it

- **Unmet demand can't be observed.** Vector reports its signs: full flights, flights scheduled but not flown,
  delays and their causes, the forecast and the runways. Then it says which constraint the evidence points to: the
  airfield and airspace, or the terminal and gates.
- **The FAA forecast is demand without capacity limits**, not what an airport can serve.
- **Rank ranges.** Each ranked airport gets its best and worst rank under other weightings. A wide range means the
  ranking depends on the weights, and the answer says so.
- **Missing data.** A KPI without a value is left out and its weight goes to the others. The answer names the missing
  source when it changes the conclusion.
- **Mixed evidence.** The prompt asks the model to say how sure it is when the evidence points both ways.
- **Every number is checked** against the tools' results before the answer is shown ([AI](AI.md)).

## What the score doesn't know

Construction costs, airport finances and debt, airline lease terms, projects already under way or funded, local
passenger caps and curfews, and competition between airports of one metro area. Treat the ranking as a screen of
where to look, not an investment case.

## Operating limits

- One analyst on one machine: chat memory is in memory and keyed by conversation, not by user.
- No public deploy: see [Setup](SETUP.md#why-there-is-no-public-url).
- A data refresh takes about 4 minutes, up to 8 when every BTS year changed. Meanwhile, memory holds two copies of
  the data.
