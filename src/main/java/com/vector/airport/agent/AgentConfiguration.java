package com.vector.airport.agent;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** The agent's beans beside its controller. */
@Configuration(proxyBeanMethods = false)
class AgentConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentConfiguration.class);

    /**
     * A tool's failure as JSON, e.g. {@code {"error": "No airport has the code [LA]; look it up with find"}}, logged:
     * Gemini reads every tool answer as JSON, and Spring AI's default answers plain text, which fails the chat.
     */
    @Bean
    ToolExecutionExceptionProcessor toolErrors(JsonMapper json) {
        return failure -> {
            log.warn("Tool {} failed: {}", failure.getToolDefinition().name(), failure.getMessage());
            return json.writeValueAsString(Map.of("error", failure.getMessage()));
        };
    }
}
