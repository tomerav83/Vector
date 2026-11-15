package com.vector.airport.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.vector.airport.FixtureDataTest;
import java.util.List;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** An answer carries the state of each source it cites: the period loaded, or why nothing loaded. */
@FixtureDataTest
class EvidenceTest {

    @Autowired
    DSLContext db;

    @Test
    void carriesTheStateOfEachSourceItCites() {
        assertThat(Evidence.create(db, List.of(), List.of(), "faa-register", "bts-segments")
                        .sources())
                .extracting("name", "period", "problem")
                .containsExactly(tuple("bts-segments", "2026-01-01", null), tuple("faa-register", "2026-10-29", null));
    }

    @Test
    void sourceTheActiveProfilesDoNotHaveFails() {
        assertThatThrownBy(() -> Evidence.create(db, List.of(), List.of(), "bts-segments", "bts-segment"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No source [bts-segment] in the active profiles");
    }
}
