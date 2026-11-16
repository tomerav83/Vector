-- FAA calendar-year boardings. The report has one column per year ("CY 25 Enplanements", "CY 24 Enplanements"), read by
-- header pattern so the year comes from the file itself, and each airport's hub size in the report's year.
select error('needs faa-register')
where not exists (select * from airport);

insert into boardings by name
-- One row per airport of the report.
with faa_boardings as (
    select * from read_sheet({file})
),

-- One row per airport and year column: the column's header and its value, e.g. BOS, CY 25 Enplanements, 21021153.
yearly as (
    unpivot faa_boardings
    on columns('^CY \d\d Enplanements$')
    into name header value boardings
)

select
    Locid as airport_id,
    -- 'CY 25 Enplanements' is year 2025
    year(strptime(regexp_extract(header, '^CY (\d\d) Enplanements$', 1), '%y')) as year,
    cast(boardings as bigint) as boardings
from yearly
where Locid in (select airport.airport_id from airport);

-- Hub: L/M/S/N = large/medium/small/nonhub primary airports; "None" = nonprimary commercial service.
update airport
set
    hub = case faa_boardings.Hub
        when 'L' then 'LARGE'
        when 'M' then 'MEDIUM'
        when 'S' then 'SMALL'
        when 'N' then 'NONHUB'
        when 'None' then 'NONPRIMARY'
        else error(concat('Unknown FAA hub size: ', faa_boardings.Hub))
    end
from read_sheet({file}) as faa_boardings
where airport.airport_id = faa_boardings.Locid;
