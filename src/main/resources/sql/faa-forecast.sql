-- FAA Terminal Area Forecast: boardings and operations per fiscal year. TAF scenario 0 = actual, 1 = forecast.
-- Boardings = air carrier + air taxi + commuter + US-flag and foreign-flag international.
-- Operations = itinerant (air carrier, air taxi, general aviation, military) + local (general aviation, military).
select error('needs faa-register') where not exists (select * from airport);

insert into forecast (airport_id, year, is_forecast, boardings, operations)
select e.locid, cast(e.ayear as integer),
       case e.scenario when '0' then false
                       when '1' then true
                       else error('Unknown TAF scenario: ' || e.scenario) end,
       cast(cast(e.aac as double) + cast(e.aat as double) + cast(e.commuter as double)
            + cast(e.us_flag as double) + cast(e.frgn_flag as double) as bigint),
       cast(cast(o.itn_Ac as double) + cast(o.itn_at as double) + cast(o.itn_ga as double)
            + cast(o.itn_mil as double) + cast(o.loc_ga as double) + cast(o.loc_mil as double) as bigint)
from read_sheet({file:Enplanements.xlsx}) e
left join read_sheet({file:AirportsOperations.xlsx}) o on o.locid = e.locid and o.ayear = e.ayear
join airport a on a.airport_id = e.locid;

-- Metro groups: TAF names each airport's hub (HUB_LOCID); the hub belongs to its own group.
insert into metro (hub_id, airport_id)
select t.HUB_LOCID, t.LOCID
from read_sheet({file:Airports.xlsx}) t
join airport a on a.airport_id = t.LOCID
join airport hub on hub.airport_id = t.HUB_LOCID
union
select t.HUB_LOCID, t.HUB_LOCID
from read_sheet({file:Airports.xlsx}) t
join airport hub on hub.airport_id = t.HUB_LOCID;
