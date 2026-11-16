package com.vector.airport.data.downloader.impl;

import com.vector.airport.data.downloader.Downloader;
import com.vector.airport.data.source.SourceFile;
import com.vector.airport.data.web.Web;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/** A file the site already keeps at its URL, so one GET downloads it. */
@Component(SourceFile.STORED)
class Stored implements Downloader {

    private final Web web;

    Stored(Web web) {
        this.web = web;
    }

    @Override
    public void download(SourceFile file, Path target) {
        web.download(file.url(), target);
    }
}
