package com.vector.airport.data;

import com.vector.airport.data.source.Source;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

/**
 * {@code vector.data.*}, bound by Spring Boot: a misspelled key fails the start and names the key, e.g. {@code The
 * elements [vector.data.sources.faa-register.perod] were left unbound}.
 *
 * @param cacheDir local copies of the downloaded files, kept across starts (gitignored)
 * @param lock the committed record of which period of each source to load, e.g. {@code data/sources.lock.json}
 * @param refresh load the newest period of every source instead of the locked one, then rewrite the lock
 * @param frozen never download and never rewrite the lock, as cargo's {@code --frozen}: a source the lock lacks fails
 *     the start, and a locked file missing from the cache leaves its source out; the committed fixtures start so
 * @param sql the folder of {@code schema.sql}, {@code finish.sql}, {@code views.sql} and one {@code <source>.sql} per
 *     source
 * @param retries how often {@code Web} makes a failed call again (a timeout, an HTTP 503); its {@code @Retryable} reads
 *     it, and the value in {@code application.yml}
 * @param backoff the wait before the first retry, e.g. {@code 5s}; it doubles for each one after
 * @param sources every source by name, in load order; each country's profile adds its own ({@code application-us.yml})
 */
@ConfigurationProperties(prefix = "vector.data", ignoreUnknownFields = false)
record DataProperties(
        Path cacheDir,
        Path lock,
        boolean refresh,
        boolean frozen,
        @DefaultValue("classpath:sql/") Resource sql,
        int retries,
        Duration backoff,
        @DefaultValue Map<String, Source> sources) {

    /** @throws IllegalStateException without a country's profile, where the sources come from */
    DataProperties {
        if (sources.isEmpty()) {
            throw new IllegalStateException(
                    "No sources: start with a country's profile, e.g. --spring.profiles.active=us");
        }
    }
}
