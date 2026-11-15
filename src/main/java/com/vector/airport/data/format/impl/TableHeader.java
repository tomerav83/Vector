package com.vector.airport.data.format.impl;

import com.vector.airport.data.format.Formats;
import java.nio.file.Path;
import java.util.Set;
import org.apache.tika.mime.MediaType;
import org.springframework.stereotype.Component;

/**
 * A table header alone, e.g. {@code year,quarter}: how a source answers for a period not published yet, also inside a
 * BTS zip.
 */
@Component
public class TableHeader extends Plain {

    /** One non-blank line, as {@code custom-mimetypes.xml} adds it to Tika's types. */
    @Override
    public Set<MediaType> types() {
        return Set.of(MediaType.text("x-table-header"));
    }

    @Override
    public boolean hasRows(Path file, Formats formats) {
        return false;
    }
}
