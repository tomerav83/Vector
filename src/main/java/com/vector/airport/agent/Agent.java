package com.vector.airport.agent;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The analyst's chat partner at {@code POST /api/chat}: the configured LLM ({@code spring.ai.model.chat}) following
 * {@code agent/system.md}, answering with the tools' figures and keeping each conversation's recent messages for
 * follow-ups. Without a model ({@code none}) it answers 503, saying how to set one. Its tools are those of every
 * {@link ToolCallbackProvider} bean; with {@code spring.ai.mcp.server.enabled} they are also an MCP server at
 * {@code /mcp}, for Claude Code or any MCP client.
 */
@RestController
class Agent {

    private static final Logger log = LoggerFactory.getLogger(Agent.class);

    private final Optional<ChatClient> llm;

    /**
     * Builds on Spring AI's auto-configured builder, so {@code spring.ai.tools.*}, observations and builder customizers
     * apply; {@code ChatClient.builder(model)} would bypass them.
     *
     * @param tools every tool bean, e.g. {@code Airports}; the MCP server serves the same beans
     */
    Agent(
            ObjectProvider<ChatModel> model,
            ObjectProvider<ChatClient.Builder> builder,
            ChatMemory memory,
            List<ToolCallbackProvider> tools,
            @Value("classpath:agent/system.md") Resource system) {
        this.llm = Optional.ofNullable(model.getIfAvailable())
                .map(_ -> builder.getObject()
                        .defaultSystem(system)
                        .defaultTools(tools)
                        .defaultAdvisors(
                                MessageChatMemoryAdvisor.builder(memory).build())
                        .build());
    }

    @PostMapping("/api/chat")
    Answer answer(@RequestBody Question question) {
        ChatClient chat = llm.orElseThrow(() -> new ResponseStatusException(
                SERVICE_UNAVAILABLE,
                "No LLM: restart with LLM_PROVIDER and its API key, e.g. LLM_PROVIDER=google-genai GEMINI_API_KEY=…"));
        try {
            return new Answer(chat.prompt()
                    .user(question.text())
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, question.conversation()))
                    .call()
                    .content());
        } catch (RuntimeException e) {
            String reason = NestedExceptionUtils.getMostSpecificCause(e).toString();
            log.warn("The model failed: {}", reason);
            throw new ResponseStatusException(BAD_GATEWAY, "The model failed: " + reason, e);
        }
    }

    /** 400 saying what is wrong with the question, e.g. {@code text is over 4000 characters}. */
    @ExceptionHandler
    ProblemDetail badRequest(HttpMessageNotReadableException e) {
        return ProblemDetail.forStatusAndDetail(
                BAD_REQUEST, NestedExceptionUtils.getMostSpecificCause(e).getMessage());
    }
}
