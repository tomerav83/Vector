package com.vector.airport.data.format.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vector.airport.data.format.Formats;
import com.vector.airport.data.format.FormatsTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A zip is judged by what each of its files is, fingerprinted by its data alone, and hands SQL its entries. */
class ZipTest {

    private final Formats formats = FormatsTest.formats();

    private final Zip zip = new Zip();

    @TempDir
    Path dir;

    @Test
    void needsADataRowInEveryCsv() throws IOException {
        Path t100 = zip(
                "t100.zip",
                Map.of("Documentation.csv", "Field,Description\nYEAR,Year\n", "T_T100_SEGMENT.csv", "YEAR,MONTH\n"));

        assertThat(zip.hasRows(zip("full.zip", Map.of("T.csv", "A,B\n1,2\n")), formats))
                .isTrue();
        assertThat(zip.hasRows(t100, formats)).isFalse();
    }

    @Test
    void judgesEachEntryByItsOwnFormat() throws IOException {
        Path sheet = zip("Airports.xlsx", Map.of("xl/workbook.xml", "<workbook/>"));
        Path taf = zip("taf.zip", Map.of("Airports", Files.readString(sheet, StandardCharsets.ISO_8859_1)));
        Path nasr = zip("nasr.zip", Map.of("CSV_README.pdf", "%PDF-1.7\n\0\0", "APT_BASE.csv", "A,B\n1,2\n"));
        Path headers = zip("headers.zip", Map.of("T.csv", "A,B\n"));
        Path nested = zip("nested.zip", Map.of("headers", Files.readString(headers, StandardCharsets.ISO_8859_1)));

        assertThat(zip.hasRows(taf, formats)).as("a spreadsheet passes").isTrue();
        assertThat(zip.hasRows(nasr, formats))
                .as("a PDF no format reads is skipped")
                .isTrue();
        assertThat(zip.hasRows(nested, formats))
                .as("a zip in a zip is judged by its own entries")
                .isFalse();
    }

    @Test
    void fingerprintIgnoresThePackingButNotTheData() throws IOException {
        // BTS stamps each download with the time it was made; the same data must give the same fingerprint
        Path monday = zip("monday.zip", Map.of("data.csv", "a,b\n1,2\n"), 1_000_000L);
        Path tuesday = zip("tuesday.zip", Map.of("data.csv", "a,b\n1,2\n"), 2_000_000L);
        Path revised = zip("revised.zip", Map.of("data.csv", "a,b\n1,3\n"), 1_000_000L);

        assertThat(zip.fingerprint(monday)).isEqualTo(zip.fingerprint(tuesday));
        assertThat(zip.fingerprint(monday)).isNotEqualTo(zip.fingerprint(revised));
    }

    @Test
    void extractsTheEntryASourceNames() throws IOException {
        Path taf = zip("taf.zip", Map.of("data/Airports.csv", "A,B\n1,2\n", "Enplanements.csv", "C\n3\n"));

        Path airports = zip.extract(taf, "data/Airports.csv", dir);

        assertThat(airports).content().isEqualTo("A,B\n1,2\n");
        assertThat(airports.getFileName().toString()).endsWith("-Airports.csv");
    }

    @Test
    void anEntryTheZipLacksFailsNamingTheEntriesItHolds() throws IOException {
        Path t100 = zip("t100.zip", Map.of("T100_2024.csv", "A\n1\n"));

        assertThatThrownBy(() -> zip.extract(t100, "T100.csv", dir))
                .isInstanceOf(NoSuchFileException.class)
                .hasMessage("T100.csv: the zip holds only [T100_2024.csv]");
    }

    private Path zip(String name, Map<String, String> entries) throws IOException {
        return zip(name, entries, System.currentTimeMillis());
    }

    /** Entries written as ISO-8859-1, so a zip passed in as text comes out byte for byte. */
    private Path zip(String name, Map<String, String> entries, long time) throws IOException {
        Path file = dir.resolve(name);
        try (var out = new ZipOutputStream(Files.newOutputStream(file))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                var zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTime(time);
                out.putNextEntry(zipEntry);
                out.write(entry.getValue().getBytes(StandardCharsets.ISO_8859_1));
                out.closeEntry();
            }
        }
        return file;
    }
}
