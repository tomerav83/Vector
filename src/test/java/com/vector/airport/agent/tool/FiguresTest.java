package com.vector.airport.agent.tool;

import static com.vector.airport.view.tables.Airport.AIRPORT;
import static com.vector.airport.view.tables.Segment.SEGMENT;
import static com.vector.airport.view.tables.Source.SOURCE;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.raw;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.jooq.CloseableDSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * The figures a tool computes in its jOOQ query, on rows the test writes, as {@code ViewsTest} checks the views: each
 * expected value is worked out by hand from those rows, and no two columns expect the same, so a figure under another
 * column's name fails here.
 */
class FiguresTest {

    /** A new in-memory database per test. */
    @AutoClose
    private CloseableDSLContext db;

    /** The tables of {@code schema.sql}, empty and with their constraints, the views on them, and loaded sources. */
    @BeforeEach
    void createTablesAndViews() throws IOException {
        db = DSL.using("jdbc:duckdb:");
        for (String file : List.of("sql/schema.sql", "sql/views.sql")) {
            db.execute(raw(new ClassPathResource(file).getContentAsString(StandardCharsets.UTF_8)));
        }
        for (String source : List.of("bts-segments", "faa-boardings", "faa-register")) {
            db.insertInto(SOURCE)
                    .set(SOURCE.NAME, source)
                    .set(SOURCE.TITLE, source)
                    .set(SOURCE.PAGE, "https://example.org/")
                    .set(SOURCE.PERIOD, "2026-08-01")
                    .execute();
        }
    }

    @Test
    void longHaulSplitsEachShareByPassengerAndCargo() {
        airports("MA", null, false, 1);
        flights("SCHEDULED_PASSENGER", 3500, 2);
        flights("CHARTER_PASSENGER", 500, 6);
        flights("SCHEDULED_CARGO", 4000, 3);
        flights("CHARTER_CARGO", 100, 1);

        assertThat(new Traffic(db, new AirportCodes(db))
                        .longHaul(List.of("MA0"), 3000)
                        .rows())
                .as("12 departures: 8 passenger (2 of them long haul), 4 cargo (3 of them long haul)")
                .containsExactly(Map.ofEntries(
                        entry("airport_id", "MA0"),
                        entry("first_month", "2025-09"),
                        entry("last_month", "2026-08"),
                        entry("miles", 3000),
                        entry("departures", 12L),
                        entry("long_haul_share", 0.4167),
                        entry("passenger_departures", 8L),
                        entry("passenger_long_haul_share", 0.25),
                        entry("cargo_departures", 4L),
                        entry("cargo_long_haul_share", 0.75)));
    }

    @Test
    void countCountsTheRegisterThenItsCommercialAirportsByHubSize() {
        airports("MA", "LARGE", false, 1);
        airports("RI", "MEDIUM", false, 2);
        airports("MA", "NONHUB", false, 3);
        airports("MA", "NONPRIMARY", false, 5);
        airports("MA", null, false, 7);
        airports("MA", null, true, 4);

        assertThat(new Airports(db).count(null).rows())
                .as("22 in the register, 4 closed; 11 commercial, 5 of them under 10,000 boardings a year")
                .containsExactly(Map.ofEntries(
                        entry("airports", 22),
                        entry("open_airports", 18),
                        entry("commercial_airports", 11),
                        entry("primary_airports", 6),
                        entry("large_hubs", 1),
                        entry("medium_hubs", 2),
                        entry("small_hubs", 0),
                        entry("nonhubs", 3),
                        entry("nonprimary_airports", 5)));
    }

    @Test
    void countKeepsTheGivenStatesInAnyCase() {
        airports("MA", "LARGE", false, 1);
        airports("RI", "MEDIUM", false, 2);

        assertThat(new Airports(db).count(List.of("ri")).rows())
                .extracting("airports")
                .containsExactly(2);
    }

    /** Inserts {@code n} airports of {@code state}, e.g. RI0 and RI1, each after its state and hub. */
    private void airports(String state, String hub, boolean closed, int n) {
        IntStream.range(0, n)
                .forEach(i -> db.insertInto(AIRPORT)
                        .set(AIRPORT.AIRPORT_ID, state + (hub == null ? "" : hub) + (closed ? "C" : "") + i)
                        .set(AIRPORT.COUNTRY, "US")
                        .set(AIRPORT.NAME, "AIRPORT " + i)
                        .set(AIRPORT.CITY, "CITY")
                        .set(AIRPORT.STATE, state)
                        .set(AIRPORT.LATITUDE, 42.0)
                        .set(AIRPORT.LONGITUDE, -71.0)
                        .set(AIRPORT.CLOSED, closed)
                        .set(AIRPORT.HUB, hub)
                        .execute());
    }

    /** {@code departures} in the newest month from MA0, the first airport {@link #airports} wrote for MA. */
    private void flights(String serviceClass, double miles, long departures) {
        db.insertInto(SEGMENT)
                .set(SEGMENT.YEAR, 2026)
                .set(SEGMENT.MONTH, 8)
                .set(SEGMENT.CARRIER, "AA")
                .set(SEGMENT.ORIGIN_ID, "MA0")
                .set(SEGMENT.ORIGIN, "MA0")
                .set(SEGMENT.ORIGIN_COUNTRY, "US")
                .set(SEGMENT.DEST, "LHR")
                .set(SEGMENT.DEST_COUNTRY, "GB")
                .set(SEGMENT.SERVICE_CLASS, serviceClass)
                .set(SEGMENT.DEPARTURES_PERFORMED, departures)
                .set(SEGMENT.PASSENGERS, 0L)
                .set(SEGMENT.DISTANCE_MI, miles)
                .execute();
    }
}
