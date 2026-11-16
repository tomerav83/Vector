package com.vector.airport.data.format;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.apache.tika.mime.MediaType;

/** A file of a media type no format reads, e.g. {@code application/pdf} where a table was expected. */
final class UnknownFormat extends IOException {

    UnknownFormat(MediaType type, Collection<Format> formats) {
        super("%s is not a kind of file the app reads; it reads %s".formatted(type, names(formats)));
    }

    private static List<String> names(Collection<Format> formats) {
        return formats.stream()
                .map(format -> format.getClass().getSimpleName())
                .distinct()
                .sorted()
                .toList();
    }
}
