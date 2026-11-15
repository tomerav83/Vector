package com.vector.airport.agent;

/**
 * What the agent answers a {@link Question}: the JSON body of {@code POST /api/chat}, e.g. {@code {"text": "…"}}.
 *
 * @param text the answer in plain text, as {@code agent/system.md} asks
 */
record Answer(String text) {}
