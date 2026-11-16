# The data

Everything Vector knows comes from public US government files that need no API key. The first start downloads about
40 MB of them (about 3 minutes). Every start then loads them into an in-memory DuckDB database in about 10 seconds.

## Sources

| Source key | Publisher and file | What it gives | Published |
|---|---|---|---|
| `faa-register` | FAA airport register (NASR) | Every airport: codes, position, status, runways | Every 28 days |
| `bts-airports` | BTS master airport list | The codes BTS statistics use, matched to FAA airports | Current state only |
| `faa-boardings` | FAA passenger boardings (CY) | Boardings per year, hub size (large, medium, small, nonhub) | Yearly |
| `faa-forecast` | FAA Terminal Area Forecast (TAF) | Boardings and operations to 2055, metro areas | Yearly |
| `bts-segments` | BTS T-100 segments | Monthly passengers, seats, flights and distance per route leg | Monthly, about 3 months behind |
| `bts-delays` | BTS airline on-time delay causes | Monthly arrivals, delays and their causes per airport | Monthly |
| `dot-fares` | DOT airfare report, Table 7 | Each airport's fare above or below the US average for the distance | Quarterly |
| `faa-slots-capped`, `faa-slots-managed` | FAA slot administration web pages | Airports where the FAA caps flights, or manages them at peak hours | When the FAA updates the page |

Each source is an entry in `src/main/resources/application-us.yml` and a SQL file, `src/main/resources/sql/<key>.sql`,
that checks and loads it. The key is the same in both, and in the tables.

## One official source per fact

Each fact comes from one official source. Airport identity and runways come from the FAA register, and hub size from
FAA boardings. Traffic comes from BTS T-100. Crowd-sourced lists (such as OurAirports) were left out: their
"scheduled service" and type flags are not reliable enough to pick commercial airports or hub sizes.

BTS and DOT use airline codes (IATA), while the FAA uses its own. The two are matched by position, within about 2 km
in the same state. Tests pin the hard cases: Mesa is AZA to airlines and IWA to the FAA.

## Pinned like a package lock

`data/sources.lock.json` (committed) records, for each source, which period loads and a fingerprint of each file's
rows. A fresh clone downloads those periods, so two people get the same answers.

- A file that changed upstream since the lock is loaded anyway and flagged (`matches_lock: false` at
  `/actuator/data`).
- A refresh (`curl -X POST 127.0.0.1:8081/actuator/data`) moves each source to its newest period. The old data keeps
  serving until the new data is complete. Then review the lock's diff and commit it.

## When a source fails

No single source stops the app. A source that can't be downloaded after 2 retries, or fails its checks, is left out.

- `curl 127.0.0.1:8081/actuator/health` turns `DOWN` and names it.
- Every tool result names the state of the sources it read, so the answer says which figures are missing and why.

## How fresh is it

Rates and traffic use the **newest 12 months** in the data, and each answer states the months. BTS publishes T-100
about 3 months late, and the newest month can still lack late-reporting airlines. The forecast is the FAA's latest
yearly TAF.

## Known gaps

- Delay data covers only the airlines that report on-time statistics, the largest US carriers. Small airports have
  none.
- Fare data covers the lower 48 states only.
- Palm Beach (DJT) has no forecast: TAF 2025 still files it under its old code, PBI.
- Passenger caps and curfews are local rules, not in any federal file (Santa Ana's cap, for example).
- Runway spacing isn't in the register, so Vector can't tell whether parallel runways work in bad weather.
- A fresh clone can't reproduce a file that grows (the BTS year files) or one published only as its current state
  (the BTS list). It loads them and flags the difference.
- The FAA publishes slot levels only as web pages. Their SQL reads the airport codes from one sentence of each page,
  and fails loudly if the sentence changes.
