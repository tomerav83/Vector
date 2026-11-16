package com.vector.airport.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.select;

import com.vector.airport.FixtureDataTest;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

/** The in-memory DuckDB the tables live in, with the driver settings of {@code application.yml}. */
@FixtureDataTest
class DatabaseTest {

    @Autowired
    HikariDataSource pool;

    @Autowired
    DSLContext db;

    /** The pool may close every connection, e.g. at its max lifetime; without jdbc_pin_db the tables would vanish. */
    @Test
    void outlivesThePoolsLastConnection() throws SQLException {
        String url = "jdbc:duckdb:memory:" + UUID.randomUUID();
        try (Connection first = DriverManager.getConnection(url, pool.getDataSourceProperties())) {
            DSL.using(first, SQLDialect.DUCKDB)
                    .createTable("kept")
                    .as(select(inline(1).as("value")))
                    .execute();
        }

        try (Connection second = DriverManager.getConnection(url, pool.getDataSourceProperties())) {
            assertThat(DSL.using(second, SQLDialect.DUCKDB)
                            .fetchValue(select(field(name("value"))).from(name("kept"))))
                    .isEqualTo(1);
        }
    }

    /**
     * After the build, whatever its SQL, a query reaches only the downloads; that the build reads them is UsDataTest.
     */
    @ParameterizedTest(name = "{2}")
    @CsvSource(delimiter = '|', textBlock = """
            select * from read_text('pom.xml')              | disabled by configuration                | read a file outside the downloads
            select * from read_text('https://example.org/') | disabled by configuration                | read a URL
            copy (select 1) to 'target/leak.csv'            | disabled by configuration                | write a file
            load httpfs                                     | Loading external extensions is disabled  | load an extension
            set enable_external_access = true               | Cannot enable external access            | turn file access on again
            set memory_limit = '1TB'                        | the configuration has been locked        | change a setting
            """)
    void aQueryReachesOnlyTheDownloads(String sql, String error, String cannot) {
        assertThatThrownBy(() -> db.execute(sql)).as(cannot).hasMessageContaining(error);
    }
}
