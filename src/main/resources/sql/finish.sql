-- After every source: the code and name airline statistics use for each airport, the newest code of its BTS id. When
-- several BTS ids sit at one airport (Yuma: NYL and YUM), the one carrying the most passengers wins.
update airport
set iata = p.code, display_name = p.display_name
from (
    select b.airport_id, b.code, b.display_name
    from bts_airport b
    left join (select c.bts_id, sum(s.passengers) as passengers
               from segment s join bts_code c on c.code = s.origin
               group by c.bts_id) t on t.bts_id = b.bts_id
    qualify row_number() over (partition by b.airport_id
                               order by coalesce(t.passengers, 0) desc, b.code) = 1
) p
where airport.airport_id = p.airport_id;
