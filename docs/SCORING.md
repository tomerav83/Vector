# How the score works

The score answers one question: **where would a bigger terminal be used, and pay off?** That takes three things:
passenger demand that keeps growing, runways and airspace with room for more flights, and passengers who pay well.

The score is plain arithmetic in Java (`Peers`, `Weighting`). The same airports and weights always give the same
ranking. The language model never computes it; it only passes the airports and any weights the analyst asks for.

## The five KPIs

| KPI | Default weight | What it measures | Better when | Source |
|---|---|---|---|---|
| Growth | 30 | Yearly growth of the boardings the FAA forecasts for the next 10 years | Higher | FAA Terminal Area Forecast |
| Scale | 25 | Boardings added in those 10 years | Higher | FAA Terminal Area Forecast |
| Headroom | 20 | Air traffic system (NAS) delay minutes per arrival, newest 12 months | **Lower** | BTS delay causes |
| Fare premium | 15 | How much more than the US average passengers pay for trips of the same distance | Higher | DOT airfare Table 7 |
| Load factor | 10 | Passengers per seat on scheduled flights, newest 12 months | Higher | BTS T-100 segments |

Why these weights:

- **Demand weighs most (growth + scale = 55).** A terminal earns from passengers. The FAA forecast is the official
  view of demand without capacity limits. Growth finds fast risers; scale keeps a small airport from winning on a
  big percentage of a small number.
- **Headroom (20).** The FAA's capacity study (FACT3) found that runways, not gates, limit most airports. NAS delay is
  what full runways and airspace look like. A new terminal there adds no flights.
- **Fare premium (15).** Passengers who pay more mean more revenue per passenger: retail, parking and fees.
- **Load factor (10).** Full planes show demand today. Airlines also steer it by cutting seats, so it weighs least.

Weights count relative to each other. Analysts can change them in chat ("weight headroom 50"). A KPI left at 0 drops
out. Negative weights, or all zeros, are refused with a reason.

## From values to a score

1. **Pick the peers.** The airports the question is about are ranked against each other. "New England" means its 16
   commercial airports. "PVD or BOS?" means just those two. One airport alone, or a question about the whole US, is
   ranked among every US airport that has a score.
2. **Turn each value into a percentile.** For each KPI, the share of peers the airport beats, a tie counting half.
   For headroom, where lower is better, the percentile is flipped.
3. **Weight and add.** Each KPI's percentile times its share of the weights gives its points. The points add up to a
   score from 0 to 100.
4. **Rank.** Highest score first. Airports with the same score share a place (1-2-2-4).

This is how composite indicators are built in the OECD/JRC handbook, and it is the shape of the FAA's own project
ranking. Percentiles are not thrown off by one huge airport, which a min-max scale would be.

### Worked example (made-up airports)

Four airports W, X, Y and Z, and airport X's values. With four peers and no ties, the percentiles are 12.5%, 37.5%,
62.5% and 87.5%. X has no fare data, as with an Alaskan airport, so the other weights grow to fill the 15 points.

| KPI | X among the 4 | Percentile beaten | Share of weight | Points |
|---|---|---|---|---|
| Growth | 1st | 87.5% | 30 / 85 = 35.3% | 30.9 |
| Scale | 2nd | 62.5% | 25 / 85 = 29.4% | 18.4 |
| Headroom | 3rd (2nd most delay) | 37.5% | 20 / 85 = 23.5% | 8.8 |
| Fare premium | no value | | 0% | 0 |
| Load factor | 4th | 12.5% | 10 / 85 = 11.8% | 1.5 |
| **Score** | | | | **59.6** |

The answer then names what lifts X (fast growth) and what holds it back (emptier flights, some airfield delay).

## How sure the ranking is

Each airport also gets its **best and worst rank** across other reasonable weightings: all KPIs equal, and each KPI
left out in turn (the OECD handbook's robustness check). An airport ranked 3rd that stays between 2nd and 4th is a
solid pick. One ranked 3rd that could be anywhere from 1st to 9th depends on the weights, and the answer says so.

## Missing values

- A KPI without a value is left out, and the other weights grow to fill it. This counts the airport at its own
  average on that KPI.
- Fare premiums exist for the lower 48 states only, so Alaska and Hawaii are scored on four KPIs.
- Small airports have no delay data: only the largest airlines report it.
- An airport with no value for any weighted KPI is not ranked, and the answer says so.

## What the score does not know

Construction costs, airport finances, lease terms, projects already under way, local caps such as Santa Ana's
passenger limit, and the competition between airports in one metro area. It is a screen that says where to look, not
an investment case. See [Scope](SCOPE.md).

## Where it lives

| What | Where |
|---|---|
| The KPIs, weights and meanings | `Kpi` |
| Each KPI's value per airport | the `candidate` view in `src/main/resources/sql/views.sql` |
| Percentiles, points and rank ranges | `Peers` |
| Weights, their checks and the robustness variations | `Weighting` |
| The `rank` tool the model calls | `Scores` |
