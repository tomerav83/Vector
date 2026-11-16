package com.vector.airport.data.source;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.util.Assert;

/**
 * Where and how one file downloads, e.g. the BTS T-100 form page and the year it posts. The lock records it; a
 * {@link Source#template() source's template} is one with placeholders, which {@link Source#file(String)} fills.
 *
 * @param downloader the {@code Downloader} bean that downloads it, e.g. {@code on-demand}
 * @param url where it is published
 * @param form the choices posted to get it; {@link Form#NONE} for a stored file
 * @param format {@value #PAGE} when it is a web page that holds the data, e.g. the FAA's list of capped airports, which
 *     then counts as rows; else {@value #TABLE}, where a web page is how the site answers for a period it has not
 *     published
 */
public record SourceFile(String downloader, String url, Form form, String format) {

    /** The default downloader: a file the site already keeps at its URL. */
    public static final String STORED = "stored";

    public static final String TABLE = "table";
    public static final String PAGE = "page";

    /** @throws IllegalArgumentException for a format other than {@value #TABLE} or {@value #PAGE} */
    public SourceFile {
        Objects.requireNonNull(url, "url");
        Assert.isTrue(
                Set.of(TABLE, PAGE).contains(format),
                () -> "format is %s or %s, not %s".formatted(TABLE, PAGE, format));
    }

    /** Whether it is a web page that holds the data. */
    public boolean isPage() {
        return PAGE.equals(format);
    }

    /**
     * What the cache knows its copy by: the url, plus the form's choices after {@code #}, since one form page serves
     * every year.
     */
    public String key() {
        return form.isEmpty() ? url : url + "#" + form.encoded();
    }

    /** This file with its placeholders filled from {@code context}, e.g. {@code {year}} with {@code 2025}. */
    SourceFile render(Map<String, Object> context) {
        return new SourceFile(downloader, Template.render(url, context), form.render(context), format);
    }
}
