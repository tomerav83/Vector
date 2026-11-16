package com.vector.airport.data.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Year;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The cache knows a file by its URL, plus its form's choices when one form page serves every year. */
class SourceFileTest {

    @Test
    void storedFileIsKnownByItsUrl() {
        assertThat(new SourceFile(SourceFile.STORED, "https://example.org/stats.csv", Form.NONE, SourceFile.TABLE)
                        .key())
                .isEqualTo("https://example.org/stats.csv");
    }

    @Test
    void formFileIsKnownByItsUrlAndItsChoices() {
        Form form = new Form(Map.of("cboYear", "2025"), List.of("YEAR"));

        assertThat(new SourceFile("on-demand", "https://example.org/DL.aspx", form, SourceFile.TABLE).key())
                .isEqualTo("https://example.org/DL.aspx#cboYear=2025");
    }

    @Test
    void renderFillsTheUrlAndTheFormFields() {
        SourceFile template = new SourceFile(
                "on-demand",
                "https://example.org/stats-{year}.aspx",
                new Form(Map.of("cboYear", "{year}"), List.of("YEAR")),
                SourceFile.TABLE);

        assertThat(template.render(Map.of("year", Year.of(2025))))
                .isEqualTo(new SourceFile(
                        "on-demand",
                        "https://example.org/stats-2025.aspx",
                        new Form(Map.of("cboYear", "2025"), List.of("YEAR")),
                        SourceFile.TABLE));
    }
}
