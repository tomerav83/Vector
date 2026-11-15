package com.vector.airport.data.source;

import java.util.Map;
import java.util.Objects;

/**
 * Where and how one file downloads, e.g. the BTS T-100 form page and the year it posts. The lock records it; a
 * {@link Source#template() source's template} is one with placeholders, which {@link Source#file(String)} fills.
 *
 * @param downloader the {@link Downloader} bean that downloads it, e.g. {@code on-demand}
 * @param url where it is published
 * @param form the choices posted to get it; {@link Form#NONE} for a stored file
 */
public record SourceFile(String downloader, String url, Form form) {

    public SourceFile {
        Objects.requireNonNull(url, "url");
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
        return new SourceFile(downloader, Template.render(url, context), form.render(context));
    }
}
