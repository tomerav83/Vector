-- BTS T-100 segments: US and foreign carriers, every nonstop leg touching the US. Foreign ends keep no airport_id.
-- BTS writes counts with two decimals (26.00); DuckDB's cast to bigint reads them as whole numbers.
select error('needs bts-airports')
where not exists (select * from bts_airport);

insert into segment by name
select
    cast(bts_segments.YEAR as integer) as year,
    cast(bts_segments.MONTH as integer) as month,
    bts_segments.UNIQUE_CARRIER as carrier,
    origin_airport.airport_id as origin_id,
    bts_segments.ORIGIN as origin,
    bts_segments.ORIGIN_COUNTRY as origin_country,
    dest_airport.airport_id as dest_id,
    bts_segments.DEST as dest,
    bts_segments.DEST_COUNTRY as dest_country,
    case bts_segments.CLASS
        when 'F' then 'SCHEDULED_PASSENGER'
        when 'G' then 'SCHEDULED_CARGO'
        when 'L' then 'CHARTER_PASSENGER'
        when 'P' then 'CHARTER_CARGO'
        else error(concat('Unknown T-100 service class: ', bts_segments.CLASS))
    end as service_class,
    cast(bts_segments.DEPARTURES_SCHEDULED as bigint) as departures_scheduled,
    cast(bts_segments.DEPARTURES_PERFORMED as bigint) as departures_performed,
    cast(bts_segments.SEATS as bigint) as seats,
    cast(bts_segments.PASSENGERS as bigint) as passengers,
    cast(bts_segments.FREIGHT as double) as freight_lb,
    cast(bts_segments.MAIL as double) as mail_lb,
    cast(bts_segments.DISTANCE as double) as distance_mi
from read_csv({file:T_T100_SEGMENT_ALL_CARRIER.csv}, all_varchar = true) as bts_segments
left join bts_airport as origin_airport on bts_segments.ORIGIN_AIRPORT_ID = origin_airport.bts_id
left join bts_airport as dest_airport on bts_segments.DEST_AIRPORT_ID = dest_airport.bts_id;
