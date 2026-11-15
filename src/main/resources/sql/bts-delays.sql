-- BTS on-time delay causes, per arrival airport and carrier. Airports are named by code only (see bts_code). A few
-- carrier-months are listed with nothing reported: skipped.
select error('needs bts-airports') where not exists (select * from bts_airport);

insert into delay (year, month, airport_id, carrier, arrivals, delayed, cancelled, diverted,
                   delayed_carrier, delayed_weather, delayed_nas, delayed_security, delayed_late_aircraft,
                   delay_minutes, delay_minutes_carrier, delay_minutes_weather, delay_minutes_nas,
                   delay_minutes_security, delay_minutes_late_aircraft)
select cast(d.year as integer), cast(d.month as integer), b.airport_id, d.carrier,
       cast(d.arr_flights as double), cast(d.arr_del15 as double),
       cast(d.arr_cancelled as double), cast(d.arr_diverted as double),
       cast(d.carrier_ct as double), cast(d.weather_ct as double), cast(d.nas_ct as double),
       cast(d.security_ct as double), cast(d.late_aircraft_ct as double),
       cast(d.arr_delay as double), cast(d.carrier_delay as double), cast(d.weather_delay as double),
       cast(d.nas_delay as double), cast(d.security_delay as double), cast(d.late_aircraft_delay as double)
from read_csv({file:Airline_Delay_Cause.csv}, all_varchar = true) d
join bts_code c on c.code = d.airport
join bts_airport b on b.bts_id = c.bts_id
where d.arr_flights is not null;
