-- What the agent's tools and the score read on top of the tables of schema.sql: run in main each time a snapshot is
-- published (data.Tables), so every reader sees the same views and macros. DuckDB checks a view when it is created, so
-- a view comes after the views it reads. A macro the views call belongs here: one in schema.sql exists only inside the
-- snapshot being built. DuckDB's / divides exactly (1 / 10 = 0.1, where PostgreSQL gives 0), and x / nullif(y, 0) is
-- NULL where y is 0.

-- Every code a user may type, and its airport: FAA, ICAO, and every code BTS ever used (PBI still finds DJT).
create or replace view airport_code as
select
    airport_id as code,
    'FAA' as kind,
    airport_id
from airport

union all

select
    icao as code,
    'ICAO' as kind,
    airport_id
from airport
where icao is not null

union all

select
    bts_code.code,
    'BTS' as kind,
    bts_airport.airport_id
from bts_code
inner join bts_airport on bts_code.bts_id = bts_airport.bts_id;

-- Each monthly table's newest months, and per airport the figures over them, so an answer and a score always show the
-- same numbers. Shares are fractions: 0.2 = 20%.
create or replace macro recent_months() as 12;

-- The rows of a monthly table (year, month) in its newest recent_months(), with the first and last month they cover.
create or replace macro recent(monthly) as table
    -- One row: the table's newest month, and the month recent_months() - 1 before it.
    with bounds as (
        select
            max(make_date(year, month, 1)) - to_months(recent_months() - 1) as first_month,
            max(make_date(year, month, 1)) as last_month
        from query_table(monthly)
    )

    select
        monthly_table.*,
        strftime(bounds.first_month, '%Y-%m') as first_month,
        strftime(bounds.last_month, '%Y-%m') as last_month
    from query_table(monthly) as monthly_table
    cross join bounds
    where make_date(monthly_table.year, monthly_table.month, 1) >= bounds.first_month;

-- passenger: a scheduled or charter passenger flight; the others carry only freight and mail.
create or replace view recent_segment as
select
    *,
    service_class in ('SCHEDULED_PASSENGER', 'CHARTER_PASSENGER') as passenger
from recent(segment);

create or replace view recent_delay as
select * from recent(delay);

-- Departing traffic. Load factor and shortfall count scheduled passenger flights; shortfall only those with departures
-- scheduled, since international rows mostly report none.
create or replace view traffic as
-- One row per carrier, month and leg departing a register airport, with what decides which figures it counts in.
with flights as (
    select
        *,
        service_class = 'SCHEDULED_PASSENGER' as scheduled,
        service_class = 'SCHEDULED_PASSENGER' and departures_scheduled > 0 as planned,
        greatest(departures_scheduled - departures_performed, 0) as departures_not_flown
    from recent_segment
    where origin_id is not null
)

select
    origin_id as airport_id,
    -- the same on every row of a recent() table
    any_value(first_month) as first_month,
    any_value(last_month) as last_month,
    sum(passengers) filter (where passenger) as passengers,
    sum(seats) filter (where scheduled) as seats,
    round(sum(passengers) filter (where scheduled) / nullif(sum(seats) filter (where scheduled), 0), 4) as load_factor,
    sum(departures_scheduled) filter (where planned) as departures_scheduled,
    sum(departures_performed) filter (where planned) as departures_performed,
    sum(departures_not_flown) filter (where planned) as departures_not_flown,
    round(
        sum(departures_not_flown) filter (where planned)
        / nullif(sum(departures_scheduled) filter (where planned), 0),
        4
    ) as shortfall
from flights
group by origin_id;

-- Arrival delays, with the US-wide share for comparison. The causes split the delay minutes; NAS minutes per arrival is
-- the delay the air traffic system adds to every flight, high where runways and airspace are full.
create or replace view congestion as
select
    airport_id,
    any_value(first_month) as first_month,
    any_value(last_month) as last_month,
    cast(sum(arrivals) as bigint) as arrivals,
    round(sum(delayed) / nullif(sum(arrivals), 0), 4) as delayed_share,
    round(
        (select sum(every_airport.delayed) / nullif(sum(every_airport.arrivals), 0) from recent_delay as every_airport),
        4
    ) as us_delayed_share,
    round(sum(cancelled) / nullif(sum(arrivals), 0), 4) as cancelled_share,
    round(sum(diverted) / nullif(sum(arrivals), 0), 4) as diverted_share,
    round(sum(delay_minutes) / nullif(sum(delayed), 0), 1) as minutes_per_delayed_arrival,
    round(sum(delay_minutes_nas) / nullif(sum(arrivals), 0), 2) as nas_minutes_per_arrival,
    round(sum(delay_minutes_carrier) / nullif(sum(delay_minutes), 0), 4) as carrier_share,
    round(sum(delay_minutes_weather) / nullif(sum(delay_minutes), 0), 4) as weather_share,
    round(sum(delay_minutes_nas) / nullif(sum(delay_minutes), 0), 4) as nas_share,
    round(sum(delay_minutes_security) / nullif(sum(delay_minutes), 0), 4) as security_share,
    round(sum(delay_minutes_late_aircraft) / nullif(sum(delay_minutes), 0), 4) as late_aircraft_share
from recent_delay
group by airport_id;

-- How far past its base year the growth figures read the forecast, in years.
create or replace macro forecast_years() as 10;

-- The FAA forecast in its forecast year, forecast_years() past its base year (the newest year of actuals).
create or replace view growth as
-- One row: the base year and the forecast year.
with years as (
    select
        max(year) as base_year,
        max(year) + forecast_years() as forecast_year
    from forecast
    where not is_forecast
),

-- One row per airport, its two years side by side (each year is one row, so max() takes that row's value).
both_years as (
    select
        forecast.airport_id,
        years.base_year,
        years.forecast_year,
        max(forecast.boardings) filter (where forecast.year = years.base_year) as boardings,
        max(forecast.boardings) filter (where forecast.year = years.forecast_year) as forecast_boardings,
        max(forecast.operations) filter (where forecast.year = years.base_year) as operations,
        max(forecast.operations) filter (where forecast.year = years.forecast_year) as forecast_operations
    from forecast
    cross join years
    group by forecast.airport_id, years.base_year, years.forecast_year
)

select
    airport_id,
    base_year,
    forecast_year,
    boardings,
    forecast_boardings,
    operations,
    forecast_operations,
    forecast_boardings - boardings as added_boardings,
    -- the yearly rate that, compounded over forecast_years(), turns boardings into forecast_boardings
    round(pow(forecast_boardings / nullif(boardings, 0), 1 / forecast_years()) - 1, 4) as yearly_growth
from both_years;

-- Each US airport with commercial service (it has an FAA hub size): its boardings in the newest year of the FAA report,
-- the main airports of its metro area, and the limit the FAA sets on its flights, NONE when the FAA lists none (a
-- federal limit exists only by FAA rule or order). What find lists.
create or replace view commercial_airport as
-- One row per airport in a metro group: the group's main airports, e.g. LAX for SNA.
with metro_hubs as (
    select
        airport_id,
        string_agg(hub_id, ', ' order by hub_id) as metro
    from metro
    group by airport_id
),

-- One row per airport: its boardings in the newest calendar year of the FAA report.
newest_boardings as (
    select
        airport_id,
        year,
        boardings
    from boardings
    where year = (select max(all_years.year) from boardings as all_years)
)

select
    airport.airport_id,
    airport.iata,
    airport.name,
    airport.display_name,
    airport.city,
    airport.state,
    airport.hub,
    newest_boardings.year as boardings_year,
    newest_boardings.boardings,
    metro_hubs.metro,
    coalesce(flight_limit.flight_limit, 'NONE') as flight_limit
from airport
left join newest_boardings on airport.airport_id = newest_boardings.airport_id
left join metro_hubs on airport.airport_id = metro_hubs.airport_id
left join flight_limit on airport.airport_id = flight_limit.airport_id
where airport.hub is not null;

-- What the score reads: each primary airport (10,000+ boardings a year), its name and boardings as find gives them, and
-- one column per Kpi constant, named as the constant in lower case. NULL when the data has no value, e.g. fare_premium
-- outside the lower 48 states.
create or replace view candidate as
-- One row per airport: its premium in the newest quarter DOT published for it.
with newest_fare_premium as (
    select
        airport_id,
        premium
    from fare_premium
    qualify row_number() over (partition by airport_id order by year desc, quarter desc) = 1
)

select
    commercial_airport.airport_id,
    coalesce(commercial_airport.display_name, commercial_airport.name) as name,
    commercial_airport.hub,
    commercial_airport.boardings,
    growth.yearly_growth as growth,
    growth.added_boardings as scale,
    congestion.nas_minutes_per_arrival as headroom,
    newest_fare_premium.premium as fare_premium,
    traffic.load_factor
from commercial_airport
left join growth on commercial_airport.airport_id = growth.airport_id
left join congestion on commercial_airport.airport_id = congestion.airport_id
left join traffic on commercial_airport.airport_id = traffic.airport_id
left join newest_fare_premium on commercial_airport.airport_id = newest_fare_premium.airport_id
where commercial_airport.hub in ('LARGE', 'MEDIUM', 'SMALL', 'NONHUB');
