package com.vector.airport.data.format.impl;

import com.vector.airport.data.format.Formats;
import java.nio.file.Path;
import java.util.Set;
import org.apache.tika.mime.MediaType;
import org.springframework.stereotype.Component;

/** A spreadsheet: a zip holding a workbook, its own type because each part is XML, which Zip would judge a page. */
@Component
public class Xlsx extends Zip {

    static final MediaType TYPE = MediaType.application("vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    @Override
    public Set<MediaType> types() {
        return Set.of(TYPE);
    }

    /** Always true: DuckDB's reader judges the sheets when the SQL loads them. */
    @Override
    public boolean hasRows(Path file, Formats formats) {
        return true;
    }
}
