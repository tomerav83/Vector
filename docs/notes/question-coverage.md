# Question coverage plan: Airport Investment Intelligence Agent

**Date:** 2026-11-13.

**Inputs:**
- The question-space expansion, `expansion/questions.md`: 143 questions, with full recipes for each type in §3.3.
- The gap-source sweep. Its report file couldn't be saved, so its findings are folded in here; raw probes are in `gap-sources/dl-*`.
- My own re-checks, marked ✓.

**Priority key:** P0 = must handle. P1 = build if time allows. P2 = decline politely and offer a proxy. ↑ = moved up by a newly verified source; + = a type the expansion missed.

## 1. Question types → how we answer them

| ID | Type | Example | Pri | Tool (family) | Data (fields) | Computation |
|---|---|---|---|---|---|---|
| C01 | Rank candidates | ★ "Strong terminal-expansion candidates in New England?" | P0 | rankAirports | TAF, T-100, delay causes, On-Time (+ fare premium) | Weighted percentile score within FAA hub tier; constrained airports listed separately |
| C02 | Leaderboard on one metric | "Which airports have the fullest flights?" | P1 | rankAirports(sortBy) | same | Sort by one KPI |
| C03 | Compare 2–N airports | "PVD vs MHT: which has more upside?" | P0 | rankAirports(scope=list), airportMetrics | same | Side-by-side KPIs and percentiles |
| C04 | What-if | "Weight congestion 50%", "exclude large hubs", "use 2,500 mi" | P0 (sensitivity P1) | rankAirports weights/tier; routeMix(longHaulMiles) | n/a | Deterministic recompute |
| C05 | Explain the score | "Why is BDL above PVD?", "How did you score?" | P0 | rankAirports contributions; explain | n/a | Top-3 differences in KPI contribution |
| C06 | Traffic and growth | "BOS YoY growth?", "Is OAK back to 2019?" | P0 | airportMetrics(TRAFFIC) | FAA CY enplanements, T-100, TAF ops | YoY only on carriers present in both periods |
| C07 | Forecast | "FAA forecast for BNA in 2035?" | P0 | TRAFFIC | TAF aac+aat+commuter+us_flag+frgn_flag | CAGR, added passengers |
| C08 | Seat scarcity | "Load factor at SFO?", "Scheduled flights not flown at EWR?" | P0 | TRAFFIC | T-100 PASSENGERS/SEATS, DEPARTURES_* | Load factor; shortfall (corrected, §3) |
| C09 | Peaking | "Peak hour at BOS?", "How seasonal is ACK?" | P1 | PEAKING | On-Time CRSDepTime/CRSArrTime; T-100 by month | Peak month vs average; busiest hour (domestic only) |
| C10 | Congestion | ★ "Compare LA and Santa Ana congestion" | P0 | CONGESTION | Delay causes `arr_del15, nas_delay, arr_cancelled`; On-Time `TaxiOut` | Rates and national percentiles |
| C10+ | International arrivals hall congestion | "Is BOS's international arrivals hall a bottleneck?" | P1 + | CONGESTION | CBP `awt.cbp.gov/api/waitTime` (46 airports, hourly) | Average and p90 wait, passengers per hour. The only terminal-side congestion source we can script, since TSA is blocked |
| C11 | Delay causes | "Why is SFO delayed?" | P0 | CONGESTION | Delay causes `*_delay` minutes | Share of each cause; label from the largest |
| C12 | Unmet demand and binding constraint | ★ "Unmet flight demand at SFO and why?" | P0 | diagnoseCapacity | On-Time hourly scheduled arrivals, NAS arrival rate, curated caps and restrictions, T-100 shortfall, runways, fare premium | Range: flights lost (NAS cancels + shortfall) up to scheduled arrivals above the arrival rate. Constraint = REGULATORY / AIRFIELD / TERMINAL-PRESSURE / NONE |
| C13 | Facilities | "Why can't SFO land more planes?", "Gates at PVD?" | P0 runways; P1 gates, footprint, acreage ↑ | CAPACITY | NASR APT_RWY (or OurAirports); NASR `ACREAGE`; OSM gates and terminal polygons | Parallel-runway separation from end coordinates; passengers per m² of terminal footprint (a proxy) |
| C14 | Regulatory caps | "Is SNA's cap binding?", "Which airports are slot-controlled?" | P0 (flags) | CAPACITY | Curated table: FAA Slot Administration page, SNA settlement, Federal Register | Utilization = actual/cap; years to cap = ln(cap/actual)/ln(1+g) |
| C15 | Route mix, long-haul | ★ "% of long-haul flights out of ANC" | P0 | routeMix | T-100 `DISTANCE, CLASS, DEST_COUNTRY` | Share at or above the threshold, by passenger / cargo / all |
| C15+ | Route momentum | "Which new routes did PVD gain this year?" | P1 + | routeMix | T-100 ORIGIN–DEST pairs YoY | Routes added and dropped, same-carrier basis |
| C16 | Airline concentration | "Is MHT too reliant on Southwest?" | P1 | CARRIERS | T-100 `UNIQUE_CARRIER` | Top-carrier share, HHI (operating-carrier caveat) |
| C17 | Metro area and competition | "Traffic split among LA-area airports?", "Is PWM losing passengers to BOS?" | P0 names; P1 shares; P1 leakage proxy ↑ | findAirports + TRAFFIC | TAF `HUB_LOCID`, FAA CY, Airfare T1a `citymarketid` | Enplanement shares; airport's share of its city market (O&D) |
| C18 | Fares, pricing power | "Which airports have a fare premium?" | P1 ↑ (was P2) | FARES | DOT Airfare Table 7 `d6nc-s8v6.totalperprem` | Premium vs national average at the same distance |
| C19 | Cargo | "And for cargo flights?" | P1 | routeMix(CARGO), CARGO | T-100 CLASS G/P; FAA landed weight; (+ `FREIGHT` after the re-pull) | Cargo share; landed weight YoY |
| C20 | Funding pipeline | "PVD's passenger charge and its expiry?", "Largest FAA-identified needs?" | P1 | FUNDING | NPIAS App. A, AIP FY26, ATP status, PFC xlsx | $ need, $ per enplanement, terminal grants, PFC expiry |
| C21 | Airport financials, ratings | "PWM cost per enplanement?" | P2 | explain(LIMITATIONS) | CATS Form 127: manual export only | Decline, then offer proxies (C16, C06, C20) |
| C22 | Demand drivers | "Is population growth driving BOI?" | P2 (optional ↑) | n/a | Census PEP CBSA, Gazetteer, BEA CAINC1, FRED (all keyless) | Population within N miles; growth |
| C23 | Live status | "Is SFO delayed right now?" | P1 | liveStatus | NAS Status / ASWS, METAR | Snapshot only, never scored |
| C24 | Provenance, confidence, definitions | "How sure are you?", "What counts as long-haul?" | P0 | explain + the envelope on every result | n/a | Confidence 0–3 plus flags |
| C25 | Out of scope | "Should we invest $500M in BOS?", "Heathrow vs Gatwick" | P0 | system prompt; findAirports returns NON_US | n/a | Decline, then redirect to the closest public proxy |
| X1 | Name resolution | "LA", "Logan", "KSFO", "Portland" | P0 | findAirports (also inside every tool) | OurAirports, alias table, TAF `HUB_LOCID` | Exact code → alias → fuzzy match (DuckDB `jaro_winkler_similarity`) |
| X2 | Follow-ups | "What about its cargo share?", "Same for Florida" | P0 | chat memory | n/a | LLM restates explicit codes; tools stay stateless |

## 2. Tools

Six tools cover every P0 type; a seventh is P1. Full signatures are in expansion §4.2.
- `findAirports(query)`
- `rankAirports(scope, hubTier, sortBy, limit, wGrowth, wScale, wLoadFactor, wCongestion, wShortfall, includeConstrained)`
- `airportMetrics(airports, family, period)`, where family is TRAFFIC | PEAKING | CONGESTION | CAPACITY | CARRIERS | CARGO | FARES | FUNDING
- `routeMix(airport, longHaulMiles, basis, period)`
- `diagnoseCapacity(airport, period)`
- `explain(topic, term)`
- `liveStatus(airports)` (P1)

Every result shares one envelope: `resolved, period, asOf, data, caveats[], confidence{level, flags}, sources[]`. Java computes every number, flag and caveat. The LLM picks the tool, carries context between turns, states its defaults, and narrates only the numbers it gets back.

## 3. Changes to draft7 and the recipes (verified in the data)
1. **Shortfall.** International rows mostly have `DEPARTURES_SCHEDULED = 0`. ✓ SFO 2025 CLASS F international: 17,951 scheduled vs 38,832 performed. Use Σmax(0, SCHED−PERF)/ΣSCHED over CLASS F rows with SCHED > 0.
2. **The latest T-100 month is incomplete.** Aug 2026 has 21 fewer carriers reporting, which makes JFK look −9.5% YoY. Default window: trailing 12 months ending at the last month at least 2 months old. Compute YoY on the same carriers only.
3. **Re-pull T-100** with `FREIGHT, MAIL, RAMP_TO_RAMP, DISTANCE_GROUP, DATA_SOURCE, ORIGIN_COUNTRY, AIRCRAFT_CONFIG, CARRIER_NAME`. The local extract has only 15 columns; same script.
4. **Congestion** comes from the BTS delay-cause file: 0.9 MB for 12 months instead of 12 × 276 MB On-Time files. On-Time stays only for `TaxiOut` and hourly scheduled times.
5. **Fare premium is fresh.** DOT Airfare Table 7 has 2026 Q1 for 204 airports, but no AK/HI (ANC and HNL are missing ✓). DB1B still ends at 2025 Q2, so draft7's "stale" note is obsolete.
6. **TAF `SER_VOL` is not a capacity.** DEN shows 355k against 705k actual operations.
7. **TAF `HUB_LOCID` = metro groups.**
   - LAX's group: SNA, BUR, ONT, LGB, PSP.
   - SFO's group: OAK, SJC.
   - JFK's group: EWR, LGA.
   - BOS's group: MHT, ORH (not PVD).
8. **Territories count as domestic.** PR/VI have `DEST_COUNTRY='US'`, so exclude them from "international".

## 4. Data plan (assuming the hybrid strategy)

**Ingested baseline (DuckDB):**
- **T-100 Segment:** TranStats form POST (`t100_post.py` in the prior session's scratchpad).
- **BTS delay causes, 12 months:** `GET https://www.transtats.bts.gov/OT_Delay/ot_delaycause1_DL.aspx?8n4=9ur4r%20Brn4z106u%20or69rr0%20FHGDM%20NaQ%20FHGFD`.
  - The query encodes period = year×12+month, with digits 0–9 written as letters D–M. FHGDM = Sep 2025; FHGFD = Aug 2026.
  - ✓ Contains `Airline_Delay_Cause.csv`: 22,268 rows, 2025-09 → 2026-08.
- **On-Time PREZIP:** 1–3 months, for taxi-out and the hourly schedule.
- **FAA enplanements and FAA cargo** xlsx files.
- **TAF:** `APO100_TAF_Final_2025.zip`.
- **Runways:** OurAirports `runways.csv`, or NASR `https://nfdc.faa.gov/webContent/28DaySub/extra/29_Oct_2026_APT_CSV.zip` (8.5 MB; has `ACREAGE`; use GET, since HEAD returns 503).
- **Curated `caps.csv`:** 10–15 rows, each with a citation and as-of date.
  - Slot-controlled: JFK, LGA, DCA.
  - Schedule-facilitated (Level 2): ORD, LAX, EWR, SFO.
  - Source: the FAA Slot Administration page, updated 2026-08-11.
  - Also SNA's passenger cap and curfew, and the SFO 2026 arrival restriction.

**P1 ingests (all keyless xlsx):**
- NPIAS: `https://www.faa.gov/sites/faa.gov/files/airports/planning_capacity/npias/current/ARP-NPIAS-2025-2029-AppendixA.xlsx`
- AIP FY26: `https://www.faa.gov/sites/faa.gov/files/2026-11/FY2026-AIP-grants.xlsx`
  - Includes project text: 393 grants mention "Terminal", 154 "Update Airport Master Plan".
- ATP: `https://www.faa.gov/iija/atp/ATP_Grant_Status_1-27-2026.xlsx`
- PFC: `https://www.faa.gov/sites/faa.gov/files/2026-10/arp-pfc-monthly-reports-airports-20260930-corrected.xlsx`
  - Published monthly, and the file name changes.
- OSM terminal footprints: Overpass `aeroway=terminal` in the aerodrome area, with retry/backoff.

**Live at question time:**
- NAS Status `nasstatus.faa.gov/api/airport-events`, with ASWS as fallback.
- Airfare Table 7: `https://data.transportation.gov/resource/d6nc-s8v6.json` ✓.
- Airfare Table 1a: `tfrh-tu9e`, for city-market shares.
- International T-100 SODA (`innc-gbgc`…).
- CBP wait times: `awt.cbp.gov/api/waitTime`.
- Federal Register API: `https://www.federalregister.gov/api/v1/documents.json?conditions[agencies][]=federal-aviation-administration&conditions[term]=...`

**Optional (P2) sources:**
- Census PEP: `https://www2.census.gov/programs-surveys/popest/datasets/2020-2025/metro/totals/cbsa-est2025-alldata.csv`
- Census Gazetteer 2026 counties.
- BEA income: `https://apps.bea.gov/regional/zip/CAINC1.zip`
- FRED: `fredgraph.csv?id=` (only works with curl's default user agent).
- ACI-NA `NAM2025-Top-50.xlsx`: movements including GA/military, and cargo tonnes.
- mikelor/TsaThroughput CSVs: stale, May 2025.

**Blocked:**

| Source | Why |
|---|---|
| FAA CATS Form 127 (airport financials) | Blazor/SignalR app; CSV export by hand only |
| OPSNET/ATADS | Scripted POST returns 403 |
| TSA FOIA throughput PDFs | 403 bot wall |
| J.D. Power | 403 |
| DB1B | Ends 2025 Q2 |
| Census API | Needs a key |
| NTTO airport-level arrivals | Paid |
| EMMA | No API |
| Future schedules | Paid only (OAG, Cirium) |
| FAF5 | 192–426 MB, zone-level |
| NASR | No longer carries operations counts |

## 5. Sample answers for the four exemplars
- **ANC, long-haul share of CY2025 departures ✓:**

  | Basis | ≥3,000 mi | ≥2,500 mi |
  |---|---|---|
  | All flights | 31.1% | 43.3% |
  | Cargo | 52.9% | 69.0% |
  | Passenger | 2.8% | 9.8% |

  The question says "flights", so lead with all flights and split by type immediately.
- **LAX vs SNA:**
  - 12 months to Aug 2026 ✓: arrivals delayed ≥15 min 20.2% vs 18.7%; NAS share of delay minutes 16% vs 18%.
  - Aug 2026 taxi-out: 19.1 vs 16.8 min.
  - Fare premium ✓: +2.1% vs +8.1%.
  - SNA's binding constraint is regulatory: 11.37M of an 11.8M passenger cap in 2025 (96%), and 93% of the 12.2M cap for 2026–30.
- **SFO:**
  - 12 months ✓: 33.4% of arrivals delayed ≥15 min; NAS is 48% of delay minutes (Aug 2026 alone: 30%, with late aircraft 45%).
  - Parallel runways 750 ft apart; arrival rate cut from 54 to 36/h on 2026-03-31, 40–42 with staggered approaches since 2026-08-12.
  - Fare premium +5.8% ✓; international passenger share 29.2%; TAF forecast 26M → 44M by 2045.
  - Unmet demand is given as a range and is airfield-bound: terminal money buys bigger aircraft, not more flights.
- **New England:**
  - NPIAS 2025–29 development estimate: BOS $906M, BDL $175M, PWM $111M, HVN $96M, PVD $93M, BTV $88M, MHT $72M.
  - Fare premium, 2026 Q1 ✓: BTV +12.2%, PWM −1.1%, MHT −4.4%, BDL −5.4%, BOS −5.6%, PVD −9.4%, HVN −56.6%.
  - OSM terminal footprint (m²): BOS 201,925, BDL 26,601, PVD 23,542, MHT 19,171.
  - Moody's (2019) said BOS historically absorbed the region's growth. FAA CY 2025 shows the reverse: MHT +8.3%, PVD +6.7%, BOS −0.3%.

## 6. Open decisions
- Data strategy: decided in memory as hybrid plus a committed snapshot.
- Which new sources to add beyond P0.
- The score's KPI set.
- The long-haul headline.

Defaults unless overridden: keyless only; percentiles within FAA hub tier; trailing-12-month window.
