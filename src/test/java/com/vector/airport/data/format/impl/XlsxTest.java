package com.vector.airport.data.format.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.vector.airport.data.format.Formats;
import com.vector.airport.data.format.FormatsTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.tika.mime.MimeTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A spreadsheet is told by its workbook, however far into the zip, and passes as having rows: DuckDB judges it. */
class XlsxTest {

    private final Formats formats = FormatsTest.formats();

    @TempDir
    Path dir;

    @Test
    void itsXmlPartsAreNotPages() throws IOException {
        Path sheet = dir.resolve("boardings");
        try (var out = new ZipOutputStream(Files.newOutputStream(sheet))) {
            out.putNextEntry(new ZipEntry("xl/workbook.xml"));
            out.write("<workbook/>".getBytes());
        }

        assertThat(formats.detect(sheet)).isExactlyInstanceOf(Xlsx.class);
        assertThat(formats.detect(sheet).hasRows(sheet, formats)).isTrue();
    }

    @Test
    void isToldByItsWorkbookPastWhatTikaMagicReads() throws IOException {
        Path sheet = dir.resolve("download");
        try (var out = new ZipOutputStream(Files.newOutputStream(sheet))) {
            out.setLevel(Deflater.NO_COMPRESSION);
            out.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            out.write(new byte[MimeTypes.getDefaultMimeTypes().getMinLength()]);
            out.putNextEntry(new ZipEntry("xl/workbook.xml"));
            out.closeEntry();
        }

        assertThat(formats.detect(sheet)).isExactlyInstanceOf(Xlsx.class);
    }
}
