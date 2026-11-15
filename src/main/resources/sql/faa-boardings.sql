-- FAA calendar-year boardings. The report has one column per year ("CY 25 Enplanements", "CY 24 Enplanements"), read by
-- header pattern so the year comes from the file itself, and each airport's hub size in the report's year.
select error('needs faa-register') where not exists (select * from airport);

insert into boardings (airport_id, year, boardings)
select y.Locid,
       year(strptime(regexp_extract(y.header, '^CY (\d\d) Enplanements$', 1), '%y')),
       cast(cast(y.boardings as double) as bigint)
from (unpivot (select * from read_sheet({file}))
      on columns('^CY \d\d Enplanements$') into name header value boardings) y
join airport a on a.airport_id = y.Locid;

-- Hub: L/M/S/N = large/medium/small/nonhub primary airports; "None" = nonprimary commercial service.
update airport
set hub = case h.Hub when 'L' then 'LARGE'
                     when 'M' then 'MEDIUM'
                     when 'S' then 'SMALL'
                     when 'N' then 'NONHUB'
                     when 'None' then 'NONPRIMARY'
                     else error('Unknown FAA hub size: ' || h.Hub) end
from read_sheet({file}) h
where airport.airport_id = h.Locid;
