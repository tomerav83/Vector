package com.vector.airport.data.format.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vector.airport.data.format.Formats;
import com.vector.airport.data.format.FormatsTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A text table has rows, is fingerprinted byte for byte, and holds no entries. */
class PlainTest {

    private final Formats formats = FormatsTest.formats();

    private final Plain plain = new Plain();

    @TempDir
    Path dir;

    @Test
    void aHeaderAndARowArePlain() throws IOException {
        Path table = write("a.csv", "year,quarter\n2026,1\n");

        assertThat(formats.detect(table)).isExactlyInstanceOf(Plain.class);
        assertThat(plain.hasRows(table, formats)).isTrue();
    }

    @Test
    void fingerprintIsItsSha256() throws IOException {
        assertThat(plain.fingerprint(write("abc.txt", "abc")))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void hasNoEntries() throws IOException {
        Path page = write("taf-2027.zip", "<p>We are investigating issues with TAF</p>");

        assertThatThrownBy(() -> plain.extract(page, "Airports.xlsx", dir)).hasMessageContaining("not an archive");
    }

    private Path write(String name, String content) throws IOException {
        return Files.writeString(dir.resolve(name), content);
    }
}
