-- FAA Slot Administration, Level 2 airports: one sentence names them, "the Level 2 airports include Chicago O'Hare
-- International Airport (ORD), ..."; another names the airports where only an international terminal is Level 2,
-- managed by the airport, "Those airports are currently Orlando International Airport (MCO) and ...". The codes are
-- FAA location ids, as airport_id.
select error('the FAA page names no Level 2 airports: its wording changed')
where not exists (select * from page_codes({file}, 'Level 2 airports include'));

insert into flight_limit by name
select
    code as airport_id,
    'MANAGED' as flight_limit
from page_codes({file}, 'Level 2 airports include')

union all

select
    code as airport_id,
    'TERMINAL' as flight_limit
from page_codes({file}, 'Those airports are currently');
