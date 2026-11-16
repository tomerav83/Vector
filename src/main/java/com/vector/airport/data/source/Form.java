package com.vector.airport.data.source;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The choices an on-demand download posts (BTS TranStats): fields to set and boxes to tick.
 *
 * @param fields field values in their yml order, e.g. {@code cboYear: "{year}"} (templates)
 * @param checked boxes to tick, e.g. the columns to include
 */
public record Form(
        @DefaultValue Map<String, String> fields,
        @DefaultValue List<String> checked) {

    public static final Form NONE = new Form(Map.of(), List.of());

    public boolean isEmpty() {
        return fields.isEmpty() && checked.isEmpty();
    }

    /**
     * The field values as {@code application/x-www-form-urlencoded}, e.g. {@code cboYear=2025&cboPeriod=All}; the same
     * {@link URLEncoder} encoding as the posted body, so the lock's keys never change.
     */
    public String encoded() {
        return fields.entrySet().stream()
                .map(field ->
                        URLEncoder.encode(field.getKey(), UTF_8) + "=" + URLEncoder.encode(field.getValue(), UTF_8))
                .collect(joining("&"));
    }

    /** These choices with the fields' placeholders filled from {@code context}, e.g. {@code cboYear: "2025"}. */
    Form render(Map<String, Object> context) {
        Map<String, String> filled = new LinkedHashMap<>(fields);
        filled.replaceAll((name, value) -> Template.render(value, context));
        return new Form(filled, checked);
    }
}
