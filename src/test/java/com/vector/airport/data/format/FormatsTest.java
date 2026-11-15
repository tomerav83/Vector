package com.vector.airport.data.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vector.airport.data.format.impl.Page;
import com.vector.airport.data.format.impl.Plain;
import com.vector.airport.data.format.impl.TableHeader;
import com.vector.airport.data.format.impl.Xlsx;
import com.vector.airport.data.format.impl.XlsxDetector;
import com.vector.airport.data.format.impl.Zip;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A file's kind is told by its content, never its name, and a kind no format reads fails naming its media type. */
public class FormatsTest {

    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '7', '\n', 0, 0, 1, 2};

    private final Formats formats = formats();

    @TempDir
    Path dir;

    /** Every format and detector, as Spring collects the beans. */
    public static Formats formats() {
        return new Formats(
                List.of(new Xlsx(), new Zip(), new Plain(), new Page(), new TableHeader()),
                List.of(new XlsxDetector()));
    }

    @Test
    void zipIsToldByItsSignatureWhateverItsName() throws IOException {
        Path zip = dir.resolve("data.csv");
        try (var out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("T.csv"));
            out.closeEntry();
        }

        assertThat(formats.detect(zip)).isExactlyInstanceOf(Zip.class);
    }

    @Test
    void aTypeWithoutAFormatTakesItsParents() throws IOException {
        Path table = Files.writeString(dir.resolve("download"), "a,b\n1,2\n");

        assertThat(formats.detect(table)).isExactlyInstanceOf(Plain.class);
    }

    @Test
    void theNameIsNoHint() throws IOException {
        // with Tika's name hint, this table would be text/html, a Page without rows
        Path table = Files.writeString(dir.resolve("rows.html"), "a,b\n1,2\n");

        assertThat(formats.detect(table)).isExactlyInstanceOf(Plain.class);
        assertThat(formats.detect(table).hasRows(table, formats)).isTrue();
    }

    @Test
    void aFileNoFormatReadsFailsNamingItsMediaType() throws IOException {
        Path pdf = Files.write(dir.resolve("4831948.tmp"), PDF);

        assertThatThrownBy(() -> formats.detect(pdf))
                .isInstanceOf(IOException.class)
                .hasMessage("application/pdf is not a kind of file the app reads; it reads "
                        + "[Page, Plain, TableHeader, Xlsx, Zip]");
        assertThat(formats.find(pdf)).isEmpty();
    }

    @Test
    void twoFormatsClaimingOneTypeFail() {
        assertThatThrownBy(() -> new Formats(List.of(new Plain(), new Plain()), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("text/plain");
    }
}
