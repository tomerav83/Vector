package com.vector.airport.data.source.impl;

import com.vector.airport.data.source.Downloader;
import com.vector.airport.data.source.Form;
import com.vector.airport.data.source.SourceFile;
import com.vector.airport.data.source.Unpublished;
import com.vector.airport.data.source.Web;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * A file with no URL of its own, made on demand when the site's form is posted (BTS TranStats). The form's ASP.NET
 * state fields change on every visit and are tied to the session cookie, so each download reads the page first and
 * posts them back with the file's {@link Form} choices.
 */
@Component("on-demand")
class OnDemand implements Downloader {

    /** ASP.NET's own state fields (__VIEWSTATE, __EVENTVALIDATION…); the page's other inputs are left out. */
    private static final String STATE_FIELDS = "input[name^=__]";

    /** What a browser sends for a ticked box that has no value of its own. */
    private static final String TICKED = "on";

    private final Web web;

    OnDemand(Web web) {
        this.web = web;
    }

    @Override
    public void download(SourceFile file, Path target) throws IOException {
        web.submit(file.url(), body(file.form(), web.page(file.url())), target);
    }

    /**
     * What the download button posts: the page's state fields, the field values and the ticked boxes; Spring encodes
     * it.
     *
     * @throws IOException when the page is not the download form any more: it has no state fields, or lacks a field or
     *     a box, e.g. a renamed {@code cboYear} (the site would ignore it and send its default year)
     * @throws Unpublished when a drop-down list lacks the value, e.g. a year it has no data for yet
     */
    static MultiValueMap<String, String> body(Form form, Document page) throws IOException {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        page.select(STATE_FIELDS).forEach(input -> body.set(input.attr("name"), input.val()));
        if (body.isEmpty()) {
            throw new IOException("Form page changed: no ASP.NET state fields at " + page.location());
        }
        validate(form, page);
        form.fields().forEach(body::set);
        form.checked().forEach(box -> body.set(box, TICKED));
        return body;
    }

    private static void validate(Form form, Document page) throws IOException {
        List<String> missing = Stream.concat(form.fields().keySet().stream(), form.checked().stream())
                .filter(name -> page.getElementsByAttributeValue("name", name).isEmpty())
                .toList();
        if (!missing.isEmpty()) {
            throw new IOException("Form page changed: %s has no %s".formatted(page.location(), missing));
        }
        List<String> notOffered = form.fields().entrySet().stream()
                .filter(field -> !isOffered(page, field))
                .map(Map.Entry::toString)
                .toList();
        if (!notOffered.isEmpty()) {
            throw new Unpublished("%s does not offer %s".formatted(page.location(), notOffered));
        }
    }

    /** True unless the field is a drop-down list without that value. */
    private static boolean isOffered(Document page, Map.Entry<String, String> field) {
        List<String> options =
                page.select("select[name=%s] option".formatted(field.getKey())).eachAttr("value");
        return options.isEmpty() || options.contains(field.getValue());
    }
}
