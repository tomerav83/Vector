package com.vector.airport.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** An SQL file runs a statement at a time: the rows add up, comments are skipped, and an error names its line. */
class ScriptTest {

    private DSLContext db;

    @BeforeEach
    void setUp() throws SQLException {
        db = DSL.using(DriverManager.getConnection("jdbc:duckdb:"), SQLDialect.DUCKDB);
    }

    @Test
    void executeAddsUpTheRowsEachStatementWrote() {
        Script script = new Script("t.sql", """
                -- A header, then statements that each end a line.
                create table t (a varchar);
                insert into t values ('a;b'), ('c');
                -- a comment between statements
                insert into t values ('d');
                update t set a = upper(a) where a = 'c';
                -- what follows the last statement
                """);

        assertThat(script.execute(db)).isEqualTo(4);
        assertThat(db.fetch("select a from t order by a").getValues(0, String.class))
                .containsExactly("C", "a;b", "d");
    }

    @Test
    void anErrorPointsAtTheLineInItsStatement() {
        Script script = new Script("t.sql", """
                create table t (a int);
                insert into t
                select nope from t;
                """);

        assertThatThrownBy(() -> script.execute(db)).hasMessageContaining("LINE 2: select nope from t");
    }

    @Test
    void renderFillsThePlaceholders() {
        Script script = new Script("t.sql", "select * from read_csv({file});\n");

        assertThat(script.render((name, format) -> "'" + name + ".csv'"))
                .isEqualTo(new Script("t.sql", "select * from read_csv('file.csv');\n"));
    }

    @Test
    void readTakesTheFileOfTheName() {
        assertThat(Script.read(new ClassPathResource("snapshot-test/sql/"), "list")
                        .name())
                .isEqualTo("list.sql");
        assertThatThrownBy(() -> Script.read(new ClassPathResource("snapshot-test/sql/"), "nosql"))
                .hasMessageStartingWith("Missing SQL file nosql.sql in");
    }
}
