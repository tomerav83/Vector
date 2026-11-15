package com.vector.airport.data.format.impl;

import com.vector.airport.data.format.Format;
import com.vector.airport.data.format.Formats;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.apache.tika.mime.MediaType;
import org.springframework.stereotype.Component;

/** A text table, e.g. a CSV: any text that is not a {@link Page} or a {@link TableHeader}. */
@Component
public class Plain implements Format {

    /** Any text, e.g. {@code text/csv} or JSON; not a PDF or a zip. */
    @Override
    public Set<MediaType> types() {
        return Set.of(MediaType.TEXT_PLAIN);
    }

    /** Always true: a page or a header alone is a type of its own. */
    @Override
    public boolean hasRows(Path file, Formats formats) throws IOException {
        return true;
    }

    @Override
    public String fingerprint(Path file) throws IOException {
        try (Sha256 digest = new Sha256()) {
            Files.copy(file, digest);
            return digest.hex();
        }
    }

    @Override
    public Path extract(Path file, String entry, Path dir) throws IOException {
        throw new IOException("the download is not an archive, so it has no " + entry);
    }
}
