-- FAA airport register (NASR): airports (A) and seaplane bases (C) in the US. Heliports, gliderports etc. are not
-- airline airports.
insert into airport by name
select
    ARPT_ID as airport_id,
    COUNTRY_CODE as country,
    ICAO_ID as icao,
    ARPT_NAME as name,
    CITY as city,
    STATE_CODE as state,
    REGION_CODE as region,
    cast(LAT_DECIMAL as double) as latitude,
    cast(LONG_DECIMAL as double) as longitude,
    ARPT_STATUS <> 'O' as closed
from read_csv({file:APT_BASE.csv}, all_varchar = true)
where SITE_TYPE_CODE in ('A', 'C') and COUNTRY_CODE = 'US';

-- The runways of the airports above.
insert into runway by name
select
    ARPT_ID as airport_id,
    RWY_ID as runway_id,
    cast(RWY_LEN as integer) as length_ft,
    cast(RWY_WIDTH as integer) as width_ft,
    SURFACE_TYPE_CODE as surface
from read_csv({file:APT_RWY.csv}, all_varchar = true)
where ARPT_ID in (select airport.airport_id from airport);
