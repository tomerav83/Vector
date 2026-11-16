package com.vector.airport.agent.call;

import com.fasterxml.jackson.annotation.JsonRawValue;

/**
 * One tool call that answered with rows, as the page gets it beside the text to draw them, e.g. {@code {"tool":
 * "traffic", "evidence": {"rows": […], "sources": […], "definitions": […], "limitations": […]}}}.
 *
 * @param tool the tool's name, e.g. {@code traffic}
 * @param evidence the tool's JSON as the model read it, passed on as it is
 */
public record Call(String tool, @JsonRawValue String evidence) {}
