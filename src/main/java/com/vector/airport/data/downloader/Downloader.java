package com.vector.airport.data.downloader;

import com.vector.airport.data.source.SourceFile;
import com.vector.airport.data.web.Unpublished;
import com.vector.airport.data.web.Web;
import java.io.IOException;
import java.nio.file.Path;

/**
 * One way to download a source's files: a bean a source names in {@code downloader:}, e.g. {@code on-demand}; each sits
 * in {@code impl} and sends its HTTP through {@link Web}.
 */
public interface Downloader {

    /**
     * Writes the file to {@code target}.
     *
     * @throws Unpublished when the site has not published it (yet), e.g. a 404; not tried again
     * @throws IOException when the site fails, e.g. an HTTP 503 or a timeout
     */
    void download(SourceFile file, Path target) throws IOException;
}
