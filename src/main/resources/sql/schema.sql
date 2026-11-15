-- The airport tables of every country, created empty in each new snapshot and filled by sql/<source>.sql from the
-- downloaded files, with the helpers that SQL reads them with; views.sql defines what the agent reads on top of them.
-- A column's unit is in its name (distance_mi, length_ft, freight_lb): the US sources publish these, a source that
-- publishes others converts. Missing values are NULL, never 0.
-- airport_id is the code the country's aviation authority keys its airports on: in the US the FAA location id (BOS, 1G4),
-- which every US airport has and BTS codes are matched to by position. The primary key fails a clash between countries.

-- Great-circle distance (haversine) on the mean Earth radius (IUGG).
create or replace macro earth_radius_km() as 6371.0;
create or replace macro km_between(lat1, lon1, lat2, lon2) as
    2 * earth_radius_km() * asin(sqrt(pow(sin(radians(lat2 - lat1) / 2), 2)
        + cos(radians(lat1)) * cos(radians(lat2)) * pow(sin(radians(lon2 - lon1) / 2), 2)));

-- A spreadsheet's first sheet as text under its header row, the way read_csv(..., all_varchar = true) reads a CSV:
-- cells trimmed, blank cells NULL. Columns are picked by header name, so a renamed column fails instead of shifting.
-- st_read is GDAL's xlsx reader in DuckDB's spatial extension, downloaded on first start; OGC_FID is its row number.
-- Not the excel extension's read_xlsx: it rejects a cell format without numFmtId, which the spec allows (FAA TAF).
install spatial;
load spatial;
create or replace macro read_sheet(path) as table
    select nullif(trim(columns(* exclude (OGC_FID))), '')
    from st_read(path, open_options = ['HEADERS=FORCE', 'FIELD_TYPES=STRING']);

create or replace table airport (
    airport_id   varchar primary key, -- the aviation authority's code, e.g. FAA location id BOS
    country      varchar not null,    -- ISO 3166-1 alpha-2 of that authority, e.g. US
    icao         varchar,
    iata         varchar,             -- the code airline statistics use (BTS); NULL without airline traffic
    name         varchar not null,    -- the authority's register name
    display_name varchar,             -- BTS name, when it has airline traffic
    city         varchar not null,
    state        varchar,             -- state or territory, e.g. MA
    region       varchar,             -- the authority's region, e.g. FAA ANE = New England, AWP = Western-Pacific
    hub          varchar check (hub in ('LARGE', 'MEDIUM', 'SMALL', 'NONHUB', 'NONPRIMARY')),
                                      -- FAA hub size in the newest calendar-year boardings report
    latitude     double  not null,
    longitude    double  not null,
    closed       boolean not null
);

create or replace table runway (
    airport_id varchar not null,
    runway_id  varchar not null, -- e.g. 10L/28R
    length_ft  integer not null,
    width_ft   integer,
    surface    varchar           -- FAA surface code, e.g. ASPH, CONC
);

-- BTS airport ids at a register airport. BTS keeps an id when its code changes (PBI became DJT in Aug 2026); code is the
-- id's newest.
create or replace table bts_airport (
    bts_id       varchar primary key,
    code         varchar not null,
    airport_id   varchar not null,
    display_name varchar,
    city         varchar
);

-- Every code BTS ever used, and the id that used it last: delay statistics name airports by code only.
create or replace table bts_code (
    code   varchar primary key,
    bts_id varchar not null
);

-- Passengers boarding per calendar year (FAA enplanements).
create or replace table boardings (
    airport_id varchar not null,
    year       integer not null,
    boardings  bigint  not null,
    primary key (airport_id, year)
);

-- FAA Terminal Area Forecast; the years up to its base year are actuals.
create or replace table forecast (
    airport_id  varchar not null,
    year        integer not null, -- the publisher's year; FAA: federal fiscal year (October to September)
    is_forecast boolean not null,
    boardings   bigint,
    operations  bigint,           -- takeoffs + landings
    primary key (airport_id, year)
);

-- Airports the FAA groups under one metro hub, e.g. LAX with SNA, BUR, ONT, LGB and PSP.
create or replace table metro (
    hub_id     varchar not null, -- the main airport, which belongs to its own group
    airport_id varchar not null,
    primary key (hub_id, airport_id)
);

-- One carrier's monthly traffic on one nonstop leg (BTS T-100 segment).
create or replace table segment (
    year                 integer not null,
    month                integer not null,
    carrier              varchar,
    origin_id            varchar,          -- NULL outside the US register, e.g. a foreign airport
    origin               varchar not null, -- BTS code
    origin_country       varchar not null, -- ISO 3166-1 alpha-2
    dest_id              varchar,
    dest                 varchar not null,
    dest_country         varchar not null,
    service_class        varchar not null check (service_class in
                             ('SCHEDULED_PASSENGER', 'SCHEDULED_CARGO', 'CHARTER_PASSENGER', 'CHARTER_CARGO')),
    departures_scheduled bigint,
    departures_performed bigint,
    seats                bigint,
    passengers           bigint  not null,
    freight_lb           double,
    mail_lb              double,
    distance_mi          double
);

-- One carrier's monthly arrivals at one airport and their delays by cause (BTS on-time statistics). Cause counts are
-- fractional: BTS splits a late flight across its causes by minutes.
create or replace table delay (
    year                          integer not null,
    month                         integer not null,
    airport_id                    varchar not null,
    carrier                       varchar not null,
    arrivals                      double  not null,
    delayed                       double, -- 15+ minutes late; NULL when not reported (a few cancelled-only months)
    cancelled                     double,
    diverted                      double,
    delayed_carrier               double,
    delayed_weather               double,
    delayed_nas                   double, -- air traffic system: volume, airspace, non-extreme weather
    delayed_security              double,
    delayed_late_aircraft         double,
    delay_minutes                 double,
    delay_minutes_carrier         double,
    delay_minutes_weather         double,
    delay_minutes_nas             double,
    delay_minutes_security        double,
    delay_minutes_late_aircraft   double
);

-- How much more (positive) or less passengers pay than the national average for trips of the same distance.
create or replace table fare_premium (
    airport_id       varchar not null,
    year             integer not null,
    quarter          integer not null,
    premium          double  not null, -- a fraction, as DOT publishes it: 0.081 = 8.1% more
    fared_passengers bigint,
    primary key (airport_id, year, quarter)
);
