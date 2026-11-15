package com.vector.airport.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.vector.airport.FixtureDataTest;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The in-memory DuckDB the tables live in, with the driver settings of {@code application.yml}. */
@FixtureDataTest
class DatabaseTest {

    @Autowired
    HikariDataSource pool;

    /** The pool may close every connection, e.g. at its max lifetime; without jdbc_pin_db the tables would vanish. */
    @Test
    void outlivesThePoolsLastConnection() throws SQLException {
        String url = "jdbc:duckdb:memory:" + UUID.randomUUID();
        try (Connection first = DriverManager.getConnection(url, pool.getDataSourceProperties())) {
            first.createStatement().execute("create table kept as select 1 as value");
        }

        try (Connection second = DriverManager.getConnection(url, pool.getDataSourceProperties())) {
            assertThat(DSL.using(second, SQLDialect.DUCKDB).fetchValue("select value from kept"))
                    .isEqualTo(1);
        }
    }
}
