package com.vector.airport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.inline;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.ai.model.chat=none")
class AppTest {
	@Autowired DSLContext db;

	@Test
	void jooqTalksToDuckDb() {
		assertThat(db.dialect()).isEqualTo(SQLDialect.DUCKDB);
		assertThat(db.select(inline(41).plus(1)).fetchOne(0, Integer.class)).isEqualTo(42);
	}
}