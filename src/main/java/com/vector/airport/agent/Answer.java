package com.vector.airport.agent;

import com.vector.airport.agent.call.Call;
import com.vector.airport.agent.clarification.Clarification;
import java.util.List;

/**
 * What the agent answers a {@link Question}: the JSON body of {@code POST /api/chat}, e.g. {@code {"text": "…",
 * "clarifying": false, "calls": […]}}.
 *
 * @param text the answer in a few lines of markdown, as {@code agent/system.md} asks
 * @param clarifying whether the text is a question back to the analyst ({@link Clarification}), e.g. which Portland
 * @param calls the tool calls that answered with rows, in order, which the page draws as cards
 */
record Answer(String text, boolean clarifying, List<Call> calls) {}
