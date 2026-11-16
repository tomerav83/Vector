package com.vector.airport.agent.clarification;

import java.lang.reflect.Type;
import org.springframework.ai.tool.execution.ToolCallResultConverter;

/**
 * A tool's text as it is, where Spring AI's default would quote it as JSON: {@code clarify}'s question reaches the page
 * as {@code Which one?}, not {@code "Which one?"}. Public, since Spring AI creates it.
 */
public class TextConverter implements ToolCallResultConverter {

    @Override
    public String convert(Object result, Type returnType) {
        return (String) result;
    }
}
