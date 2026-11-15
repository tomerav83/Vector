package com.vector.airport.agent.tool;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;
import static org.jooq.impl.DSL.val;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * The codes an analyst types for an airport, as the {@code airport_code} view lists them (FAA, ICAO or airline code),
 * turned into the airport ids every tool queries, e.g. KSFO into SFO.
 */
@Component
class AirportCodes {

    /** How a tool asks for airports. */
    static final String DESCRIPTION = "airport codes, e.g. [LAX, SNA]; find gives them";

    private final DSLContext db;

    AirportCodes(DSLContext db) {
        this.db = db;
    }

    /**
     * The airport id of each of {@code codes}, in their order and once each, in any case: a code as FAA code first,
     * then ICAO, then airline code.
     *
     * @throws IllegalArgumentException naming the codes no airport has, so the model looks them up with find
     */
    List<String> ids(List<String> codes) {
        List<String> normalized = codes.stream()
                .map(code -> code.strip().toUpperCase(Locale.ROOT))
                .toList();
        Map<String, String> ids = db.fetch("""
                select code, airport_id
                from airport_code
                where code = any({0})
                qualify row_number() over (partition by code order by kind = 'FAA' desc, kind = 'ICAO' desc) = 1
                """, val(normalized.toArray(String[]::new))).stream()
                .collect(toMap(row -> row.get("code", String.class), row -> row.get("airport_id", String.class)));
        List<String> unknown = normalized.stream().filter(not(ids::containsKey)).toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("No airport has the code %s; look it up with find".formatted(unknown));
        }
        return normalized.stream().map(ids::get).distinct().toList();
    }
}
