-- FAA Slot Administration, Level 3 airports: one sentence names them, "the Level 3 airports are John F. Kennedy
-- International Airport (JFK), LaGuardia Airport (LGA), and Ronald Reagan Washington National Airport (DCA)". Airlines
-- need a slot for every flight there. The codes are FAA location ids, as airport_id.
select error('the FAA page names no Level 3 airports: its wording changed')
where not exists (select * from page_codes({file}, 'Level 3 airports are'));

insert into flight_limit by name
select
    code as airport_id,
    'CAPPED' as flight_limit
from page_codes({file}, 'Level 3 airports are');
