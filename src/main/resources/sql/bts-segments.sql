-- BTS T-100 segments: US and foreign carriers, every nonstop leg touching the US. Foreign ends keep no airport_id.
select error('needs bts-airports') where not exists (select * from bts_airport);

insert into segment (year, month, carrier, origin_id, origin, origin_country, dest_id, dest, dest_country,
                     service_class, departures_scheduled, departures_performed, seats, passengers,
                     freight_lb, mail_lb, distance_mi)
select cast(s.YEAR as integer), cast(s.MONTH as integer), s.UNIQUE_CARRIER,
       o.airport_id, s.ORIGIN, s.ORIGIN_COUNTRY,
       d.airport_id, s.DEST, s.DEST_COUNTRY,
       case s.CLASS when 'F' then 'SCHEDULED_PASSENGER'
                    when 'G' then 'SCHEDULED_CARGO'
                    when 'L' then 'CHARTER_PASSENGER'
                    when 'P' then 'CHARTER_CARGO'
                    else error('Unknown T-100 service class: ' || s.CLASS) end,
       cast(cast(s.DEPARTURES_SCHEDULED as double) as bigint),
       cast(cast(s.DEPARTURES_PERFORMED as double) as bigint),
       cast(cast(s.SEATS as double) as bigint),
       cast(cast(s.PASSENGERS as double) as bigint),
       cast(s.FREIGHT as double), cast(s.MAIL as double), cast(s.DISTANCE as double)
from read_csv({file:T_T100_SEGMENT_ALL_CARRIER.csv}, all_varchar = true) s
left join bts_airport o on o.bts_id = s.ORIGIN_AIRPORT_ID
left join bts_airport d on d.bts_id = s.DEST_AIRPORT_ID;
