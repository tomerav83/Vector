package com.vector.airport.data.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The choices encode as the posted body does, and render keeps the fields in their yml order. */
class FormTest {

    @Test
    void theKeyEncodesAsTheBodyDoes() {
        // URLEncoder, as FormHttpMessageConverter: a changed encoding would change every key in the lock
        assertThat(new Form(Map.of("cboPeriod", "All Years+/"), List.of()).encoded())
                .isEqualTo("cboPeriod=All+Years%2B%2F");
    }

    @Test
    void renderFillsTheFieldsInTheirOrder() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("cboYear", "{year}");
        fields.put("cboPeriod", "All");

        assertThat(new Form(fields, List.of("YEAR"))
                        .render(Map.of("year", Year.of(2025)))
                        .fields())
                .containsExactly(entry("cboYear", "2025"), entry("cboPeriod", "All"));
    }
}
