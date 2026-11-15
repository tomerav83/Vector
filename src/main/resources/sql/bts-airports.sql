-- BTS airport list. Codes change while the BTS id stays (PBI became DJT in Aug 2026), so each id takes its newest record
-- (the "latest" flag alone is unreliable) and is matched to the register airport within same_airport_km(), preferring
-- the same code, then the nearest. BTS and FAA codes differ for some airports (Mesa Gateway: BTS AZA, FAA IWA), so
-- position decides, not code.
select error('needs faa-register') where not exists (select * from airport);

-- BTS and FAA place the same airport up to ~1 km apart (different survey points); distinct airports with airline
-- service are farther apart than this.
create or replace macro same_airport_km() as 2.0;
create or replace macro km_per_degree_latitude() as 2 * pi() * earth_radius_km() / 360;
create or replace macro bts_valid_from(start_date) as strptime(start_date, '%m/%d/%Y %I:%M:%S %p');

-- Delay statistics name airports by code only (still PBI before Aug 2026): every code points at the id that used it last.
insert into bts_code (code, bts_id)
select AIRPORT, AIRPORT_ID
from read_csv({file:T_MASTER_CORD.csv}, all_varchar = true)
where AIRPORT_COUNTRY_CODE_ISO = 'US' and LATITUDE is not null
qualify row_number() over (partition by AIRPORT order by bts_valid_from(AIRPORT_START_DATE) desc) = 1;

-- Cheap latitude band first, exact distance second.
insert into bts_airport (bts_id, code, airport_id, display_name, city)
select t.bts_id, t.code, a.airport_id, t.display_name, t.city
from (select AIRPORT_ID as bts_id, AIRPORT as code, DISPLAY_AIRPORT_NAME as display_name,
             split_part(DISPLAY_AIRPORT_CITY_NAME_FULL, ',', 1) as city,
             cast(LATITUDE as double) as lat, cast(LONGITUDE as double) as lon
      from read_csv({file:T_MASTER_CORD.csv}, all_varchar = true)
      where AIRPORT_COUNTRY_CODE_ISO = 'US' and LATITUDE is not null
      qualify row_number() over (partition by AIRPORT_ID order by bts_valid_from(AIRPORT_START_DATE) desc) = 1) t
join airport a
  on abs(a.latitude - t.lat) <= same_airport_km() / km_per_degree_latitude()
 and km_between(t.lat, t.lon, a.latitude, a.longitude) <= same_airport_km()
qualify row_number() over (partition by t.bts_id
                           order by t.code = a.airport_id desc,
                                    km_between(t.lat, t.lon, a.latitude, a.longitude)) = 1;
