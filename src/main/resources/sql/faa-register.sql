-- FAA airport register (NASR): airports (A) and seaplane bases (C) in the US. Heliports, gliderports etc. are not
-- airline airports.
insert into airport (airport_id, country, icao, name, city, state, region, latitude, longitude, closed)
select ARPT_ID, COUNTRY_CODE, ICAO_ID, ARPT_NAME, CITY, STATE_CODE, REGION_CODE,
       cast(LAT_DECIMAL as double), cast(LONG_DECIMAL as double), ARPT_STATUS <> 'O'
from read_csv({file:APT_BASE.csv}, all_varchar = true)
where SITE_TYPE_CODE in ('A', 'C') and COUNTRY_CODE = 'US';

insert into runway (airport_id, runway_id, length_ft, width_ft, surface)
select r.ARPT_ID, r.RWY_ID, cast(r.RWY_LEN as integer), cast(r.RWY_WIDTH as integer), r.SURFACE_TYPE_CODE
from read_csv({file:APT_RWY.csv}, all_varchar = true) r
join airport a on a.airport_id = r.ARPT_ID;
