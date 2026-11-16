-- BTS airport list. Codes change while the BTS id stays (PBI became DJT in Aug 2026), so each id takes its newest
-- record (the "latest" flag alone is unreliable) and is matched to the register airport within same_airport_km(),
-- preferring the same code, then the nearest. BTS and FAA codes differ for some airports (Mesa Gateway: BTS AZA, FAA
-- IWA), so position decides, not code.
select error('needs faa-register')
where not exists (select * from airport);

-- BTS and FAA place the same airport up to ~1 km apart (different survey points); distinct airports with airline
-- service are farther apart than this.
create or replace macro same_airport_km() as 2.0;
create or replace macro km_per_degree_latitude() as 2 * pi() * earth_radius_km() / 360;
create or replace macro bts_valid_from(start_date) as strptime(start_date, '%m/%d/%Y %I:%M:%S %p');

-- Delay statistics name airports by code only (still PBI before Aug 2026): every code points at the id that used it
-- last.
insert into bts_code by name
select
    AIRPORT as code,
    AIRPORT_ID as bts_id
from read_csv({file:T_MASTER_CORD.csv}, all_varchar = true)
where AIRPORT_COUNTRY_CODE_ISO = 'US' and LATITUDE is not null
qualify row_number() over (partition by AIRPORT order by bts_valid_from(AIRPORT_START_DATE) desc) = 1;

-- Each BTS id at one register airport: the nearest one with the same code, else the nearest.
insert into bts_airport by name
-- One row per BTS id: its newest record, so its current code, name and position.
with newest_record as (
    select
        AIRPORT_ID as bts_id,
        AIRPORT as code,
        DISPLAY_AIRPORT_NAME as display_name,
        split_part(DISPLAY_AIRPORT_CITY_NAME_FULL, ',', 1) as city,
        cast(LATITUDE as double) as latitude,
        cast(LONGITUDE as double) as longitude
    from read_csv({file:T_MASTER_CORD.csv}, all_varchar = true)
    where AIRPORT_COUNTRY_CODE_ISO = 'US' and LATITUDE is not null
    qualify row_number() over (partition by AIRPORT_ID order by bts_valid_from(AIRPORT_START_DATE) desc) = 1
),

-- One row per BTS id and register airport in a latitude band around it (a cheap filter before the exact distance).
nearby as (
    select
        newest_record.bts_id,
        newest_record.code,
        newest_record.display_name,
        newest_record.city,
        airport.airport_id,
        newest_record.code = airport.airport_id as same_code,
        km_between(newest_record.latitude, newest_record.longitude, airport.latitude, airport.longitude) as distance_km
    from newest_record
    inner join airport
        on abs(newest_record.latitude - airport.latitude) <= same_airport_km() / km_per_degree_latitude()
)

select
    bts_id,
    code,
    airport_id,
    display_name,
    city
from nearby
where distance_km <= same_airport_km()
qualify row_number() over (partition by bts_id order by same_code desc, distance_km asc) = 1;
