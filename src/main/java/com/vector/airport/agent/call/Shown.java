package com.vector.airport.agent.call;

import java.util.List;
import java.util.function.Consumer;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * A chat tool whose calls also reach the page, e.g. traffic's rows for SFO: each call is first reported by its tool's
 * name to the question's step consumer ({@link #STEPS}), and each call that succeeds is added to the question's
 * {@link Call} list ({@link #CALLS}), both of which {@code Agent} passes in the tool context. A failing call throws
 * past it to Spring AI, which hands the model the error, so only rows reach the page. The MCP server serves the tools
 * unwrapped.
 */
public record Shown(ToolCallback tool) implements ToolCallback {

    public static final String STEPS = "steps";

    public static final String CALLS = "calls";

    @Override
    public ToolDefinition getToolDefinition() {
        return tool.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return tool.getToolMetadata();
    }

    @Override
    public String call(String input) {
        return tool.call(input);
    }

    @Override
    @SuppressWarnings("unchecked")
    public String call(String input, ToolContext context) {
        ((Consumer<String>) context.getContext().get(STEPS))
                .accept(getToolDefinition().name());
        String evidence = tool.call(input, context);
        ((List<Call>) context.getContext().get(CALLS))
                .add(new Call(getToolDefinition().name(), evidence));
        return evidence;
    }
}
