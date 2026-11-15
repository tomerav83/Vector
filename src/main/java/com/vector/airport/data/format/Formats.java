package com.vector.airport.data.format;

import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.ParseContext;
import org.springframework.stereotype.Component;

/**
 * The {@link Format} beans by the media type Tika detects in a file, found as Tika's CompositeParser finds a parser: a
 * type with none takes its parent's, e.g. {@code text/csv} is Plain.
 */
@Component
public class Formats {

    /** Tika's types and {@code custom-mimetypes.xml}'s, each with its parent, e.g. {@code text/plain} for CSV. */
    private static final MediaTypeRegistry TYPES =
            MimeTypes.getDefaultMimeTypes().getMediaTypeRegistry();

    private final Map<MediaType, Format> formats;
    private final Detector detector;

    /**
     * @param detectors what Tika's magic cannot tell, e.g. an xlsx by its workbook
     * @throws IllegalStateException when two formats claim one type
     */
    public Formats(List<Format> formats, List<Detector> detectors) {
        this.formats = formats.stream()
                .flatMap(format -> format.types().stream().map(type -> Map.entry(type, format)))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
        this.detector = new CompositeDetector(
                TYPES,
                Stream.concat(detectors.stream(), Stream.of(MimeTypes.getDefaultMimeTypes()))
                        .toList());
    }

    /** @throws UnknownFormat for a file no format reads, e.g. a PDF where a table was expected */
    public Format detect(Path file) throws IOException {
        MediaType type = type(file);
        return find(type).orElseThrow(() -> new UnknownFormat(type, formats.values()));
    }

    /** Its format; empty for a file no format reads, e.g. the PDF readme in a NASR zip. */
    public Optional<Format> find(Path file) throws IOException {
        return find(type(file));
    }

    private Optional<Format> find(MediaType type) {
        return Stream.iterate(type, Objects::nonNull, TYPES::getSupertype)
                .filter(formats::containsKey)
                .map(formats::get)
                .findFirst();
    }

    /** By content alone: with the name as a hint, Tika makes a table named {@code rows.html} a web page. */
    private MediaType type(Path file) throws IOException {
        try (TikaInputStream content = TikaInputStream.get(file)) {
            return detector.detect(content, new Metadata(), new ParseContext());
        }
    }
}
