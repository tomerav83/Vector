package com.vector.airport.data;

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
        db.execute("create table source (name varchar primary key, period varchar, problem varchar)");
        db.execute("insert into source (name, period) values ('stats', '2026-01-01')");
    }

    @Test
    void upWhenEverySourceLoaded() {
        assertThat(new DataHealthIndicator(db).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void downNamingEachSourceLeftOutAndWhy() {
        db.execute("insert into source (name, problem) values ('list', 'java.net.ConnectException')");

        Health health = new DataHealthIndicator(db).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("leftOut", List.of(Map.of("name", "list", "problem", "java.net.ConnectException")));
    }
}
