package com.vector.airport.agent;

import java.util.UUID;
import org.springframework.util.Assert;

/**
 * What the analyst asks, in the chat page's conversation; a question without text, or longer than {@value #MAX_TEXT}
 * characters, fails with {@link IllegalArgumentException}.
 *
 * @param conversation the page's id for the conversation, which keeps follow-ups in context
 * @param text the question, e.g. "Compare LA and Santa Ana airport congestion levels."
 */
record Question(UUID conversation, String text) {

    /** About two pages: a question, not a document, which bounds what one question costs. */
    static final int MAX_TEXT = 4_000;

    Question {
        Assert.notNull(conversation, "conversation is required: the chat's id, a UUID");
        Assert.hasText(text, "text is required: the question");
        Assert.isTrue(text.length() <= MAX_TEXT, () -> "text is over %d characters".formatted(MAX_TEXT));
    }
}
