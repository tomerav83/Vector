package com.vector.airport.data;

import static com.vector.airport.view.tables.Airport.AIRPORT;
import static com.vector.airport.view.tables.Candidate.CANDIDATE;
import static com.vector.airport.view.tables.CommercialAirport.COMMERCIAL_AIRPORT;
import static com.vector.airport.view.tables.Congestion.CONGESTION;
import static com.vector.airport.view.tables.Delay.DELAY;
import static com.vector.airport.view.tables.FlightLimit.FLIGHT_LIMIT;
import static com.vector.airport.view.tables.Forecast.FORECAST;
import static com.vector.airport.view.tables.Growth.GROWTH;
import static com.vector.airport.view.tables.Segment.SEGMENT;
import static com.vector.airport.view.tables.Traffic.TRAFFIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.select;

import com.vector.airport.data.tables.Script;
import java.io.IOException;
import java.time.YearMonth;
import java.util.Map;
import java.util.Objects;
import org.jooq.CloseableDSLContext;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;

/**
 * The views of {@code sql/views.sql} on rows the test writes, as a dbt unit test checks a model: given rows of the
 * tables a view reads, the view returns the figures expected, e.g. a load factor of 0.8 from 800 passengers on 1,000
 * seats. A formula that changes fails here, naming the figure, without the downloaded files.
 */
class ViewsTest {

    private static final ClassPathResource SQL = new ClassPathResource("sql/");

    /** A new in-memory database per test. */
    @AutoClose
    private CloseableDSLContext db;

    /** The tables of {@code schema.sql}, empty and with their constraints, and the views on them. */
    @BeforeEach
    void createTablesAndViews() {
        db = DSL.using("jdbc:duckdb:");
        Script.read(SQL, "schema").execute(db);
        Script.read(SQL, "views").execute(db);
    }

    @ParameterizedTest(name = "{0} = {1}: {2}")
    @CsvSource(delimiter = '|', textBlock = """
            passengers           | 950  | scheduled and charter passenger flights
            seats                | 1000 | scheduled passenger flights only
            load_factor          | 0.8  | 800 passengers on 1,000 scheduled seats
            departures_scheduled | 10   | scheduled passenger flights
            departures_performed | 8    | of those same flights; the charter flew unscheduled
            departures_not_flown | 2    | scheduled minus performed, so the answer needs no arithmetic
            shortfall            | 0.2  | 2 of the 10 scheduled departures not flown
            """)
    void trafficCountsScheduledPassengerFlightsForLoadFactorAndShortfall(String column, double value, String reason)
            throws IOException {
        given(SEGMENT, """
                year,month,origin_id,origin,origin_country,dest,dest_country,service_class,departures_scheduled,departures_performed,seats,passengers
                2026,8,BOS,BOS,US,LAX,US,SCHEDULED_PASSENGER,10,8,1000,800
                2026,8,BOS,BOS,US,LAX,US,CHARTER_PASSENGER,0,2,300,150
                2026,8,BOS,BOS,US,ANC,US,SCHEDULED_CARGO,5,5,0,0
                """);

        assertThat(db.fetchSingle(TRAFFIC).get(column, Double.class)).as(reason).isEqualTo(value);
    }

    @ParameterizedTest(name = "{0}: {2}")
    @CsvSource(delimiter = '|', textBlock = """
            2026-07 | 110 | the month before the newest
            2025-09 | 110 | 11 months before the newest: the first of the 12
            2025-08 | 100 | 12 months before the newest: left out
            """)
    void trafficReadsTheNewest12Months(YearMonth month, long passengers, String reason) throws IOException {
        given(SEGMENT, """
                year,month,origin_id,origin,origin_country,dest,dest_country,service_class,passengers
                2026,8,BOS,BOS,US,LAX,US,SCHEDULED_PASSENGER,100
                %d,%d,BOS,BOS,US,LAX,US,SCHEDULED_PASSENGER,10
                """.formatted(month.getYear(), month.getMonthValue()));

        assertThat(db.fetchSingle(TRAFFIC).getPassengers()).as(reason).isEqualTo(passengers);
    }

    @Test
    void congestionComparesEachAirportWithTheWholeUs() throws IOException {
        given(DELAY, """
                year,month,airport_id,carrier,arrivals,delayed
                2026,8,BOS,AA,10,1
                2026,8,LAX,AA,10,3
                """);

        assertThat(db.fetchSingle(select(CONGESTION.DELAYED_SHARE, CONGESTION.US_DELAYED_SHARE)
                                .from(CONGESTION)
                                .where(CONGESTION.AIRPORT_ID.eq("BOS")))
                        .intoArray())
                .as("the US share counts every airport, though only BOS is asked for")
                .containsExactly(0.1, 0.2);
    }

    @Test
    void congestionOfAnAirportWithoutArrivalsHasNoShares() throws IOException {
        given(DELAY, """
                year,month,airport_id,carrier,arrivals,delayed,cancelled,diverted,delay_minutes,delay_minutes_nas
                2026,8,BOS,AA,0,0,0,0,0,0
                """);

        assertThat(db.fetchSingle(select(
                                        CONGESTION.DELAYED_SHARE,
                                        CONGESTION.CANCELLED_SHARE,
                                        CONGESTION.DIVERTED_SHARE,
                                        CONGESTION.NAS_MINUTES_PER_ARRIVAL)
                                .from(CONGESTION))
                        .intoList())
                .as("NULL, as for any figure without a value, not Infinity or NaN")
                .containsOnlyNulls();
    }

    @ParameterizedTest(name = "{0} to {1}: {4}")
    @CsvSource(delimiter = '|', textBlock = """
            100 | 200 | 0.0718 | 100  | doubled in 10 years: 7.18% a year
            100 | 100 | 0.0    | 0    | flat
            100 | 0   | -1.0   | -100 | down to none
            0   | 500 |        | 500  | no boardings in the base year: no rate, not Infinity
            """)
    void growthIsTheYearlyRateFromTheBaseYearToTenYearsLater(
            long boardings, long forecast, Double yearlyGrowth, long added, String reason) throws IOException {
        given(FORECAST, """
                airport_id,year,is_forecast,boardings
                BOS,2024,false,%d
                BOS,2034,true,%d
                """.formatted(boardings, forecast));

        assertThat(db.fetchSingle(select(GROWTH.YEARLY_GROWTH, GROWTH.ADDED_BOARDINGS)
                                .from(GROWTH))
                        .intoArray())
                .as(reason)
                .containsExactly(yearlyGrowth, added);
    }

    @Test
    void growthWithoutTheForecastYearHasNoRate() throws IOException {
        given(FORECAST, """
                airport_id,year,is_forecast,boardings
                BOS,2024,false,100
                BOS,2033,true,190
                """);

        assertThat(db.fetchSingle(select(GROWTH.FORECAST_YEAR, GROWTH.YEARLY_GROWTH)
                                .from(GROWTH))
                        .intoArray())
                .as("2034 is missing: NULL, not the rate to 2033")
                .containsExactly(2034, null);
    }

    @ParameterizedTest(name = "{0}: {2}")
    @CsvSource(delimiter = '|', textBlock = """
            LARGE      | 1 | a primary airport
            NONHUB     | 1 | the smallest primary airports
            NONPRIMARY | 0 | under 10,000 boardings a year
                       | 0 | not in the FAA boardings report: no airline service
            """)
    void candidateIsEveryPrimaryAirport(String hub, int candidates, String reason) throws IOException {
        given(AIRPORT, """
                airport_id,country,name,city,latitude,longitude,closed,hub
                BOS,US,GENERAL EDWARD LAWRENCE LOGAN INTL,BOSTON,42.36,-71.01,false,%s
                """.formatted(Objects.toString(hub, "")));

        assertThat(db.fetchCount(CANDIDATE)).as(reason).isEqualTo(candidates);
    }

    @Test
    void commercialAirportHasTheFaaLimitOnItsFlightsAndNoneWhenTheFaaListsIt() throws IOException {
        given(AIRPORT, """
                airport_id,country,name,city,latitude,longitude,closed,hub
                JFK,US,JOHN F KENNEDY INTL,NEW YORK,40.64,-73.78,false,LARGE
                BOS,US,GENERAL EDWARD LAWRENCE LOGAN INTL,BOSTON,42.36,-71.01,false,LARGE
                """);
        given(FLIGHT_LIMIT, """
                airport_id,flight_limit
                JFK,CAPPED
                """);

        assertThat(db.selectFrom(COMMERCIAL_AIRPORT)
                        .fetchMap(COMMERCIAL_AIRPORT.AIRPORT_ID, COMMERCIAL_AIRPORT.FLIGHT_LIMIT))
                .isEqualTo(Map.of("JFK", "CAPPED", "BOS", "NONE"));
    }

    /**
     * Inserts {@code rows}, a CSV header and a line per row, into {@code table}, each column by its header; other
     * columns, and empty values, are NULL.
     */
    private void given(Table<?> table, String rows) throws IOException {
        db.loadInto(table)
                .onErrorAbort()
                .loadCSV(rows)
                .fieldsCorresponding()
                .nullString("")
                .execute();
    }
}
