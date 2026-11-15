package com.vector.airport.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.vector.airport.LiveDataTest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Each US source on the real files of its locked period, against facts checked by hand on them; tests are named after
 * their source, as in {@link UsDataTest}. It catches upstream changes: revised or removed files, renamed columns, new
 * codes.
 */
@LiveDataTest
class UsLiveDataTest {

    /** The exam's long-haul threshold. */
    private static final int LONG_HAUL_MILES = 3000;

    @Autowired
    DSLContext db;

    @Test
    void everySourceLoadedTheLockedFiles() {
        assertThat(db.fetch("select name, problem from source where period is null or not matches_lock")
                        .intoMaps())
                .isEmpty();
    }

    @Test
    void noCodePointsAtTwoAirports() {
        assertThat(db.fetch("""
                        select code, kind from airport_code
                        group by code, kind having count(distinct airport_id) > 1
                        """).intoMaps()).isEmpty();
    }

    @Test
    void faaRegisterKeepsEveryRunway() {
        assertThat(value(Long.class, "select count(*) from runway where airport_id = 'DEN'"))
                .isEqualTo(6);
    }

    @Test
    void faaRegisterPlacesEachAirportInItsFaaRegion() {
        assertThat(db.fetch("select airport_id from airport where region = 'ANE' and hub is not null order by 1")
                        .getValues(0, String.class))
                .contains("BDL", "BOS", "BTV", "MHT", "PVD", "PWM");
    }

    @ParameterizedTest(name = "{0} shows as {1}: {2}")
    @CsvSource(delimiter = '|', textBlock = """
            BOS | BOS | the same code everywhere
            IWA | AZA | Mesa Gateway: FAA IWA, airlines AZA
            GPI | FCA | Glacier Park: FAA GPI, airlines FCA
            DJT | DJT | was PBI until Aug 2026
            1G4 | 1G4 | Grand Canyon West: statistics use the FAA code, not IATA GCW
            GUM | GUM | a territory: BTS state code TT
            SJU | SJU | San Juan, Puerto Rico
            """)
    void btsAirportsMatchesCodesByPositionNotByCode(String airportId, String iata, String why) {
        assertThat(iata(airportId)).as(why).isEqualTo(iata);
    }

    @Test
    void btsAirportsFindsAnOldCode() {
        assertThat(value(String.class, "select airport_id from airport_code where kind = 'BTS' and code = 'PBI'"))
                .isEqualTo("DJT");
    }

    @Test
    void btsSegmentsGiveAlmostAllUsTrafficAnAirport() {
        assertThat(value(Double.class, """
                        select sum(passengers) filter (where origin_id is null) / sum(passengers)
                        from segment where origin_country = 'US'
                        """)).isLessThan(0.0001);
    }

    @Test
    void btsSegmentsGiveAnchoragesLongHaulShareOfCy2025Departures() {
        // verified from T-100 by hand: all 31.1%, cargo 52.9%, passenger 2.8%
        assertThat(longHaulShare("true")).isCloseTo(31.1, within(0.3));
        assertThat(longHaulShare("service_class like '%CARGO'")).isCloseTo(52.9, within(0.3));
        assertThat(longHaulShare("service_class like '%PASSENGER'")).isCloseTo(2.8, within(0.3));
    }

    @Test
    void faaBoardingsFindEveryReportedAirportInTheRegister() {
        // the CY2025 report lists 516 airports with boardings
        assertThat(value(Long.class, "select count(*) from boardings where year = 2025"))
                .isEqualTo(516);
    }

    @Test
    void faaBoardingsMatchTheCy2025Report() {
        assertThat(value(Long.class, "select boardings from boardings where airport_id = 'ATL' and year = 2025"))
                .isEqualTo(51_459_786L);
    }

    @Test
    void faaForecastAddsUpTheActualBoardings() {
        // TAF FY2024 actual = air carrier + air taxi + commuter + US-flag and foreign-flag international
        assertThat(value(Long.class, "select boardings from forecast where airport_id = 'ATL' and year = 2024"))
                .isEqualTo(52_270_575L);
    }

    @Test
    void faaForecastGroupsEachMetroAreaUnderItsHub() {
        assertThat(db.fetch("select airport_id from metro where hub_id = 'LAX'").getValues(0, String.class))
                .contains("LAX", "SNA", "BUR", "ONT", "LGB", "PSP");
        assertThat(db.fetch("select airport_id from metro where hub_id = 'BOS'").getValues(0, String.class))
                .contains("MHT", "ORH")
                .doesNotContain("PVD");
    }

    @Test
    void dotFaresGiveThePremiumOf2026Q1() {
        assertThat(premium("SNA")).isCloseTo(0.081, within(0.001));
        assertThat(premium("LAX")).isCloseTo(0.021, within(0.001));
        assertThat(premium("BTV")).isCloseTo(0.122, within(0.001));
    }

    @Test
    void dotFaresCoverTheLower48Only() {
        assertThat(value(Long.class, "select count(*) from fare_premium where airport_id = 'ANC'"))
                .as("DOT Table 7 covers the lower 48 only")
                .isZero();
    }

    private <T> T value(Class<T> type, String sql, Object... bindings) {
        return db.fetchSingle(sql, bindings).get(0, type);
    }

    private String iata(String airportId) {
        return value(String.class, "select iata from airport where airport_id = ?", airportId);
    }

    private double premium(String airportId) {
        return value(Double.class, "select premium from fare_premium where airport_id = ?", airportId);
    }

    private double longHaulShare(String filter) {
        return value(Double.class, """
                select 100.0 * sum(departures_performed) filter (where distance_mi >= ?) / sum(departures_performed)
                from segment
                where origin_id = 'ANC' and year = 2025 and %s
                """.formatted(filter), LONG_HAUL_MILES);
    }
}
