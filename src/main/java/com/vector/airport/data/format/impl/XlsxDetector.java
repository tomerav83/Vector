package com.vector.airport.data.format.impl;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.ProviderNotFoundException;
import java.util.zip.ZipException;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.springframework.stereotype.Component;

/**
 * An xlsx by its {@code xl/workbook.xml} entry, read from the zip's directory, so past the 64 KB Tika's magic reads and
 * inside another zip; Tika's own OOXML detector would bring in Apache POI.
 */
@Component
public class XlsxDetector implements Detector {

    @Override
    public MediaType detect(TikaInputStream in, Metadata metadata, ParseContext context) throws IOException {
        try (FileSystem zip = FileSystems.newFileSystem(in.getPath())) {
            return Files.exists(zip.getPath("xl/workbook.xml")) ? Xlsx.TYPE : MediaType.OCTET_STREAM;
        } catch (ProviderNotFoundException | ZipException _) {
            return MediaType.OCTET_STREAM;
        }
    }
}
