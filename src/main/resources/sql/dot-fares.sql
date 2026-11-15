-- DOT Consumer Airfare Report, Table 7: the premium is published as a fraction of the national average fare.
select error('needs bts-airports') where not exists (select * from bts_airport);

insert into fare_premium (airport_id, year, quarter, premium, fared_passengers)
select b.airport_id, cast(f.year as integer), cast(f.quarter as integer),
       cast(f.totalperprem as double), cast(cast(f.totalfaredpax as double) as bigint)
from read_csv({file}, all_varchar = true) f
join bts_airport b on b.bts_id = f.airportid;
