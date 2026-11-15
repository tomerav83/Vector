package com.vector.airport.data.format.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.vector.airport.data.format.Formats;
import com.vector.airport.data.format.FormatsTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** HTML, even one tag on one line, and XML are pages whatever their name, and a page has no rows. */
class PageTest {

    private final Formats formats = FormatsTest.formats();

    @TempDir
    Path dir;

    @Test
    void aWebPageHasNoRows() throws IOException {
        Path page = write("t100.zip", "\r\n<!DOCTYPE html>\n<html>No data for 2027</html>\n");

        assertThat(formats.detect(page)).isExactlyInstanceOf(Page.class);
        assertThat(formats.detect(page).hasRows(page, formats)).isFalse();
    }

    @Test
    void anyTagStartsAPageEvenOnOneLine() throws IOException {
        assertThat(formats.detect(write("taf-2027.zip", "<p>We are investigating issues with TAF</p>")))
                .isExactlyInstanceOf(Page.class);
        assertThat(formats.detect(write("taf-2027.csv", "<p>We are investigating</p>\n<p>issues with TAF</p>\n")))
                .isExactlyInstanceOf(Page.class);
    }

    @Test
    void anXmlDocumentIsAPage() throws IOException {
        Path error = write("fares.csv", "<?xml version=\"1.0\"?>\n<Error><Code>NoSuchKey</Code></Error>\n");

        assertThat(formats.detect(error)).isExactlyInstanceOf(Page.class);
    }

    private Path write(String name, String content) throws IOException {
        return Files.writeString(dir.resolve(name), content);
    }
}
