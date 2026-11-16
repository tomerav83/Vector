package com.vector.airport.data.format.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.vector.airport.data.format.Formats;
import com.vector.airport.data.format.FormatsTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** One non-blank line is a table header alone, whatever its name, and has no rows. */
class TableHeaderTest {

    @TempDir
    Path dir;

    private final Formats formats = FormatsTest.formats();

    @Test
    void oneLineIsAHeaderAlone() throws IOException {
        Path header = Files.writeString(dir.resolve("fares-2027.csv"), "\nyear,quarter\r\n");

        assertThat(formats.detect(header)).isExactlyInstanceOf(TableHeader.class);
        assertThat(formats.detect(header).hasRows(header, formats)).isFalse();
    }
}
