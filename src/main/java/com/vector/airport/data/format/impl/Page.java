package com.vector.airport.data.format.impl;

import com.vector.airport.data.format.Formats;
import java.nio.file.Path;
import java.util.Set;
import org.apache.tika.mime.MediaType;
import org.springframework.stereotype.Component;

/**
 * A web page where a file was expected, e.g. TAF's "We are investigating issues": how a site answers for a period it
 * has not published.
 */
@Component
public class Page extends Plain {

    /** HTML, even one tag on one line ({@code custom-mimetypes.xml}), and XML, e.g. an error document. */
    @Override
    public Set<MediaType> types() {
        return Set.of(MediaType.TEXT_HTML, MediaType.APPLICATION_XML);
    }

    @Override
    public boolean hasRows(Path file, Formats formats) {
        return false;
    }
}
