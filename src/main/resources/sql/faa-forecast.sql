-- FAA Terminal Area Forecast: boardings and operations per fiscal year. TAF scenario 0 = actual, 1 = forecast.
-- Boardings = air carrier + air taxi + commuter + US-flag and foreign-flag international.
-- Operations = itinerant (air carrier, air taxi, general aviation, military) + local (general aviation, military).
select error('needs faa-register')
where not exists (select * from airport);

select error('needs bts-airports')
where not exists (select * from bts_airport);

-- One row per id the TAF may use for a register airport: the airport's id, or the id it had before the FAA renamed it
-- after this TAF came out (PBI is now DJT). BTS keeps the old code on the airport's BTS id, so an old id is a code of a
-- BTS id whose newest code is the register id. Only then: the TAF lists other facilities too, e.g. PCT, the Potomac
-- TRACON, while BTS's PCT is Princeton airport (39N).
create or replace macro faa_id() as table
    select
        airport_id as locid,
        airport_id
    from airport

    union all

    select
        bts_code.code as locid,
        bts_airport.airport_id
    from bts_code
    inner join bts_airport on bts_code.bts_id = bts_airport.bts_id
    where
        bts_airport.code = bts_airport.airport_id
        and bts_code.code <> bts_airport.code
        and bts_code.code not in (select airport.airport_id from airport);

insert into forecast by name
select
    faa_id.airport_id,
    cast(enplanements.ayear as integer) as year,
    case enplanements.scenario
        when '0' then false
        when '1' then true
        else error(concat('Unknown TAF scenario: ', enplanements.scenario))
    end as is_forecast,
    cast(enplanements.aac as bigint)
    + cast(enplanements.aat as bigint)
    + cast(enplanements.commuter as bigint)
    + cast(enplanements.us_flag as bigint)
    + cast(enplanements.frgn_flag as bigint) as boardings,
    cast(airports_operations.itn_Ac as bigint)
    + cast(airports_operations.itn_at as bigint)
    + cast(airports_operations.itn_ga as bigint)
    + cast(airports_operations.itn_mil as bigint)
    + cast(airports_operations.loc_ga as bigint)
    + cast(airports_operations.loc_mil as bigint) as operations
from read_sheet({file:Enplanements.xlsx}) as enplanements
left join read_sheet({file:AirportsOperations.xlsx}) as airports_operations
    on enplanements.locid = airports_operations.locid and enplanements.ayear = airports_operations.ayear
inner join faa_id() as faa_id on enplanements.locid = faa_id.locid;

-- Metro groups: TAF names each airport's hub (HUB_LOCID); the hub belongs to its own group.
insert into metro by name
-- One row per TAF airport whose hub is a register airport.
with taf_airports as (
    select
        LOCID as airport_id,
        HUB_LOCID as hub_id
    from read_sheet({file:Airports.xlsx})
    where HUB_LOCID in (select airport.airport_id from airport)
)

select
    hub_id,
    airport_id
from taf_airports
where airport_id in (select airport.airport_id from airport)

union distinct

select
    hub_id,
    hub_id as airport_id
from taf_airports;
