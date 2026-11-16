-- After every source: the code and name airline statistics use for each airport, the newest code of its BTS id. When
-- several BTS ids sit at one airport (Yuma: NYL and YUM), the one carrying the most passengers wins.
-- One row per BTS id with traffic: the passengers departing under it, over every month loaded.
with departing as (
    select
        bts_code.bts_id,
        sum(segment.passengers) as passengers
    from segment
    inner join bts_code on segment.origin = bts_code.code
    group by bts_code.bts_id
),

-- One row per airport: its busiest BTS id; without traffic, its first code.
busiest as (
    select
        bts_airport.airport_id,
        bts_airport.code,
        bts_airport.display_name
    from bts_airport
    left join departing on bts_airport.bts_id = departing.bts_id
    qualify row_number() over (
        partition by bts_airport.airport_id
        order by coalesce(departing.passengers, 0) desc, bts_airport.code asc
    ) = 1
)

update airport
set
    iata = busiest.code,
    display_name = busiest.display_name
from busiest
where airport.airport_id = busiest.airport_id;
