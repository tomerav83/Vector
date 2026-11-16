package com.vector.airport.data.downloader;

import com.vector.airport.data.source.Source;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/** The {@link Downloader} beans by name, e.g. {@code on-demand}. */
@Component
public class Downloaders {

    private final Map<String, Downloader> downloaders;

    public Downloaders(Map<String, Downloader> downloaders) {
        this.downloaders = downloaders;
    }

    /**
     * Checks that every source names a downloader bean, so a typo fails the start.
     *
     * @throws IllegalArgumentException naming the source and the beans there are, e.g. {@code on-demnd}
     */
    public void validate(Map<String, Source> sources) {
        sources.forEach((name, source) -> forName(source.template().downloader(), name));
    }

    /**
     * The bean named {@code downloader}.
     *
     * @param source who names it, for the error
     * @throws IllegalArgumentException when no bean has that name
     */
    public Downloader forName(String downloader, String source) {
        return Optional.ofNullable(downloaders.get(downloader))
                .orElseThrow(() -> new IllegalArgumentException("%s: unknown downloader '%s'; there are %s"
                        .formatted(source, downloader, new TreeSet<>(downloaders.keySet()))));
    }
}
