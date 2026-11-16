package com.vector.airport.data;

import static com.vector.airport.view.tables.Source.SOURCE;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

/** The data is DOWN while the {@code source} table holds a source left out, and names it with its problem. */
class DataHealthIndicatorTest {

    private DSLContext db;

    @BeforeEach
    void setUp() throws SQLException {
        db = DSL.using(DriverManager.getConnection("jdbc:duckdb:"), SQLDialect.DUCKDB);
        db.createTable(SOURCE).columns(SOURCE.fields()).execute();
        db.insertInto(SOURCE)
                .set(SOURCE.NAME, "stats")
                .set(SOURCE.TITLE, "stats")
                .set(SOURCE.PAGE, "https://example.org/")
                .set(SOURCE.PERIOD, "2026-01-01")
                .execute();
    }

    @Test
    void upWhenEverySourceLoaded() {
        assertThat(new DataHealthIndicator(db).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void downNamingEachSourceLeftOutAndWhy() {
        db.insertInto(SOURCE)
                .set(SOURCE.NAME, "list")
                .set(SOURCE.TITLE, "list")
                .set(SOURCE.PAGE, "https://example.org/")
                .set(SOURCE.PROBLEM, "java.net.ConnectException")
                .execute();

        Health health = new DataHealthIndicator(db).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("leftOut", List.of(Map.of("name", "list", "problem", "java.net.ConnectException")));
    }
}
