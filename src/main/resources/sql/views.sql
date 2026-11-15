-- What the agent's tools and the score read on top of the tables of schema.sql: run in main each time a snapshot is
-- published (data.Tables), so every reader sees the same views and macros. DuckDB checks a view when it is created, so
-- a view comes after the views it reads. A macro the tools call belongs here: one in schema.sql exists only inside the
-- snapshot being built.

-- Every code a user may type, and its airport: FAA, ICAO, and every code BTS ever used (PBI still finds DJT).
create or replace view airport_code as
select airport_id as code, 'FAA' as kind, airport_id from airport
union all
select icao, 'ICAO', airport_id from airport where icao is not null
union all
select c.code, 'BTS', b.airport_id from bts_code c join bts_airport b on b.bts_id = c.bts_id;

-- Each monthly table's newest months, and per airport the figures over them, so an answer and a score always show the
-- same numbers. Shares are fractions: 0.2 = 20%.
create or replace macro recent_months() as 12;

-- The rows of a monthly table (year, month) in its newest recent_months(), with the first and last month they cover.
create or replace macro recent(monthly) as table
select t.*,
       strftime(n.month - to_months(recent_months() - 1), '%Y-%m') as first_month,
       strftime(n.month, '%Y-%m') as last_month
from query_table(monthly) t, (select max(make_date(year, month, 1)) as month from query_table(monthly)) n
where make_date(t.year, t.month, 1) > n.month - to_months(recent_months());

-- passenger: a scheduled or charter passenger flight; the others carry only freight and mail.
create or replace view recent_segment as
select *, service_class like '%PASSENGER' as passenger from recent(segment);

create or replace view recent_delay as
select * from recent(delay);

-- Departing traffic. Load factor and shortfall count scheduled passenger flights; shortfall only those with departures
-- scheduled, since international rows mostly report none.
create or replace view traffic as
with flights as (
    select *, service_class = 'SCHEDULED_PASSENGER' as scheduled, scheduled and departures_scheduled > 0 as planned
    from recent_segment)
select origin_id as airport_id,
       any_value(first_month) as first_month,
       any_value(last_month) as last_month,
       sum(passengers) filter (where passenger) as passengers,
       sum(seats) filter (where scheduled) as seats,
       round(sum(passengers) filter (where scheduled) / nullif(sum(seats) filter (where scheduled), 0), 4) as load_factor,
       sum(departures_scheduled) filter (where planned) as departures_scheduled,
       sum(departures_performed) filter (where planned) as departures_performed,
       round(sum(greatest(departures_scheduled - departures_performed, 0)) filter (where planned)
             / nullif(sum(departures_scheduled) filter (where planned), 0), 4) as shortfall
from flights
where origin_id is not null
group by origin_id;

-- Arrival delays, with the US-wide share for comparison. The causes split the delay minutes; NAS minutes per arrival is
-- the delay the air traffic system adds to every flight, high where runways and airspace are full.
create or replace view congestion as
select airport_id,
       any_value(first_month) as first_month,
       any_value(last_month) as last_month,
       cast(sum(arrivals) as bigint) as arrivals,
       round(sum(delayed) / sum(arrivals), 4) as delayed_share,
       round(sum(sum(delayed)) over () / sum(sum(arrivals)) over (), 4) as us_delayed_share,
       round(sum(cancelled) / sum(arrivals), 4) as cancelled_share,
       round(sum(diverted) / sum(arrivals), 4) as diverted_share,
       round(sum(delay_minutes) / nullif(sum(delayed), 0), 1) as minutes_per_delayed_arrival,
       round(sum(delay_minutes_nas) / sum(arrivals), 2) as nas_minutes_per_arrival,
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
select *,
       forecast_boardings - boardings as added_boardings,
       round(pow(forecast_boardings / nullif(boardings, 0), 1 / forecast_years()) - 1, 4) as yearly_growth
from (select f.airport_id,
             b.year as base_year,
             b.year + forecast_years() as forecast_year,
             max(f.boardings) filter (where f.year = b.year) as boardings,
             max(f.boardings) filter (where f.year = b.year + forecast_years()) as forecast_boardings,
             max(f.operations) filter (where f.year = b.year) as operations,
             max(f.operations) filter (where f.year = b.year + forecast_years()) as forecast_operations
      from forecast f, (select max(year) as year from forecast where not is_forecast) b
      group by f.airport_id, b.year);

-- What the score reads: each primary airport (10,000+ boardings a year) with one column per Kpi constant, named as the
-- constant in lower case. NULL when the data has no value, e.g. fare_premium outside the lower 48 states.
create or replace view candidate as
select a.airport_id,
       a.hub,
       g.yearly_growth           as growth,
       g.added_boardings         as scale,
       c.nas_minutes_per_arrival as headroom,
       f.premium                 as fare_premium,
       t.load_factor
from airport a
left join growth g using (airport_id)
left join congestion c using (airport_id)
left join traffic t using (airport_id)
left join (select airport_id, premium
           from fare_premium
           qualify row_number() over (partition by airport_id order by year desc, quarter desc) = 1) f
    using (airport_id)
where a.hub <> 'NONPRIMARY';
