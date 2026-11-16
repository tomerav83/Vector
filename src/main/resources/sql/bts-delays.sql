-- BTS on-time delay causes, per arrival airport and carrier. Airports are named by code only (see bts_code). A few
-- carrier-months are listed with nothing reported: skipped.
select error('needs bts-airports')
where not exists (select * from bts_airport);

insert into delay by name
select
    cast(bts_delays.year as integer) as year,
    cast(bts_delays.month as integer) as month,
    bts_airport.airport_id,
    bts_delays.carrier,
    cast(bts_delays.arr_flights as double) as arrivals,
    cast(bts_delays.arr_del15 as double) as delayed,
    cast(bts_delays.arr_cancelled as double) as cancelled,
    cast(bts_delays.arr_diverted as double) as diverted,
    cast(bts_delays.carrier_ct as double) as delayed_carrier,
    cast(bts_delays.weather_ct as double) as delayed_weather,
    cast(bts_delays.nas_ct as double) as delayed_nas,
    cast(bts_delays.security_ct as double) as delayed_security,
    cast(bts_delays.late_aircraft_ct as double) as delayed_late_aircraft,
    cast(bts_delays.arr_delay as double) as delay_minutes,
    cast(bts_delays.carrier_delay as double) as delay_minutes_carrier,
    cast(bts_delays.weather_delay as double) as delay_minutes_weather,
    cast(bts_delays.nas_delay as double) as delay_minutes_nas,
    cast(bts_delays.security_delay as double) as delay_minutes_security,
    cast(bts_delays.late_aircraft_delay as double) as delay_minutes_late_aircraft
from read_csv({file:Airline_Delay_Cause.csv}, all_varchar = true) as bts_delays
inner join bts_code on bts_delays.airport = bts_code.code
inner join bts_airport on bts_code.bts_id = bts_airport.bts_id
where bts_delays.arr_flights is not null;
