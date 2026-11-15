package com.vector.airport.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vector.airport.data.source.Downloader;
import com.vector.airport.data.source.Form;
import com.vector.airport.data.source.Source;
import com.vector.airport.data.source.SourceFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The committed lock: JSON sorted by source for readable diffs, read back as written; a key it does not know fails. */
class LockTest {

    private static final Lock LOCK = new Lock(Map.of(
            "stats",
            new LockedSource(
                    "2026-01-01",
                    List.of(new LockedFile(
                            new SourceFile(Downloader.STORED, "https://example.org/stats-2026.csv", Form.NONE),
                            "ba7816bf"))),
            "list",
            new LockedSource(Source.CURRENT, List.of())));

    @TempDir
    Path dir;

    @Test
    void writesEachSourceInNameOrder() {
        LOCK.write(dir.resolve("sources.lock.json"));

        assertThat(dir.resolve("sources.lock.json")).content().isEqualTo("""
                {
                  "sources" : {
                    "list" : {
                      "period" : "current",
                      "files" : [ ]
                    },
                    "stats" : {
                      "period" : "2026-01-01",
                      "files" : [ {
                        "file" : {
                          "downloader" : "stored",
                          "url" : "https://example.org/stats-2026.csv",
                          "form" : {
                            "fields" : { },
                            "checked" : [ ]
                          }
                        },
                        "fingerprint" : "ba7816bf"
                      } ]
                    }
                  }
                }
                """);
    }

    @Test
    void readsWhatItWrote() {
        LOCK.write(dir.resolve("sources.lock.json"));

        assertThat(Lock.read(dir.resolve("sources.lock.json"))).isEqualTo(LOCK);
    }

    @Test
    void keyItDoesNotKnowFails() throws IOException {
        Path lock = Files.writeString(dir.resolve("sources.lock.json"), "{\"sources\": {}, \"version\": 2}");

        assertThatThrownBy(() -> Lock.read(lock)).hasMessageContaining("\"version\"");
    }
}
