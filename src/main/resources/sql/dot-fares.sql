-- DOT Consumer Airfare Report, Table 7: the premium is published as a fraction of the national average fare.
select error('needs bts-airports')
where not exists (select * from bts_airport);

insert into fare_premium by name
select
    bts_airport.airport_id,
    cast(dot_fares.year as integer) as year,
    cast(dot_fares.quarter as integer) as quarter,
    cast(dot_fares.totalperprem as double) as premium,
    cast(dot_fares.totalfaredpax as bigint) as fared_passengers
from read_csv({file}, all_varchar = true) as dot_fares
inner join bts_airport on dot_fares.airportid = bts_airport.bts_id;
