package com.vector.airport.data.format;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import org.apache.tika.mime.MediaType;

/**
 * One kind of downloaded file, a bean found by the media type of the file's content whatever its name, e.g.
 * {@code application/zip}.
 */
public interface Format {

    /** The media types it reads, each with every subtype no other format claims, e.g. Plain's {@code text/plain}. */
    Set<MediaType> types();

    /**
     * False for a web page or a table header without rows, how sources answer for a period not published yet; a
     * container, e.g. a zip, asks {@code formats} about each file it holds.
     */
    boolean hasRows(Path file, Formats formats) throws IOException;

    /** SHA-256 of its data, as hex. */
    String fingerprint(Path file) throws IOException;

    /** The file {@code entry} it holds, extracted into {@code dir} for SQL to read. */
    Path extract(Path file, String entry, Path dir) throws IOException;
}
