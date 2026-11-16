package com.vector.airport.agent;

import static org.springframework.ai.model.tool.ToolExecutionResult.FINISH_REASON;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE;

import com.vector.airport.agent.call.Call;
import com.vector.airport.agent.call.Shown;
import com.vector.airport.agent.clarification.Clarification;
import com.vector.airport.agent.grounding.GroundingAdvisor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The analyst's chat partner at {@code POST /api/chat}: the configured LLM ({@code spring.ai.model.chat}) following
 * {@code agent/system.md}, answering with the tools' figures and keeping each conversation's recent messages for
 * follow-ups. Without a model ({@code none}) it answers 503, saying how to set one. Its tools are those of every
 * {@link ToolCallbackProvider} bean; with {@code spring.ai.mcp.server.enabled} they are also an MCP server at
 * {@code /mcp}, for Claude Code or any MCP client. Beside the text, the answer carries the rows of the tools it called,
 * which the page draws; a figure no tool returned is held back ({@link GroundingAdvisor}). While the model works, the
 * page shows each tool it calls, as ChatGPT and Claude show their steps.
 */
@RestController
class Agent {

    private static final Logger log = LoggerFactory.getLogger(Agent.class);

    private final Optional<ChatClient> llm;

    private final TaskExecutor executor;

    /**
     * Builds on Spring AI's auto-configured builder, so {@code spring.ai.tools.*}, observations and builder customizers
     * apply; {@code ChatClient.builder(model)} would bypass them.
     *
     * @param tools every tool bean, e.g. {@code Airports}, each tool {@link Shown}; the MCP server serves the same
     *     beans
     * @param executor Spring Boot's task executor, which answers each streamed question
     */
    Agent(
            ObjectProvider<ChatModel> model,
            ObjectProvider<ChatClient.Builder> builder,
            ChatMemory memory,
            List<ToolCallbackProvider> tools,
            @Value("classpath:agent/system.md") Resource system,
            TaskExecutor executor) {
        this.llm = Optional.ofNullable(model.getIfAvailable())
                .map(_ -> builder.getObject()
                        .defaultSystem(system)
                        .defaultToolCallbacks(shown(tools))
                        .defaultTools(new Clarification())
                        .defaultAdvisors(
                                MessageChatMemoryAdvisor.builder(memory).build(), new GroundingAdvisor())
                        .build());
        this.executor = executor;
    }

    /**
     * The answer as server-sent events: a {@code step} per tool the model calls, its name as text (e.g.
     * {@code traffic}), then the {@code answer} ({@link Answer}) or an {@code error} (a problem detail, e.g. 502 when
     * the model fails), both JSON. A 400 or a 503 comes before the stream opens.
     */
    @PostMapping(value = "/api/chat", produces = TEXT_EVENT_STREAM_VALUE)
    SseEmitter chat(@RequestBody Question question) {
        ChatClient chat = llm.orElseThrow(Agent::unavailable);
        SseEmitter stream = new SseEmitter();
        executor.execute(() -> {
            try {
                send(stream, "answer", answer(chat, question, tool -> send(stream, "step", tool)));
            } catch (ResponseStatusException e) {
                send(stream, "error", e.getBody());
            }
            stream.complete();
        });
        return stream;
    }

    /** What the model answers {@code question}, each tool it calls reported first to {@code steps}, by its name. */
    Answer answer(Question question, Consumer<String> steps) {
        return answer(llm.orElseThrow(Agent::unavailable), question, steps);
    }

    /** 400 saying what is wrong with the question, e.g. {@code text is over 4000 characters}. */
    @ExceptionHandler
    ProblemDetail badRequest(HttpMessageNotReadableException e) {
        return ProblemDetail.forStatusAndDetail(
                BAD_REQUEST, NestedExceptionUtils.getMostSpecificCause(e).getMessage());
    }

    /** Every tool of {@code tools}, {@link Shown}. */
    private static List<ToolCallback> shown(List<ToolCallbackProvider> tools) {
        return tools.stream()
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .<ToolCallback>map(Shown::new)
                .toList();
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                SERVICE_UNAVAILABLE,
                "No LLM: restart with LLM_PROVIDER and its API key, e.g. LLM_PROVIDER=google-genai GEMINI_API_KEY=…");
    }

    private Answer answer(ChatClient chat, Question question, Consumer<String> steps) {
        List<Call> calls = new ArrayList<>();
        try {
            ChatResponse reply = chat.prompt()
                    .user(question.text())
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, question.conversation()))
                    .toolContext(Map.of(Shown.CALLS, calls, Shown.STEPS, steps))
                    .call()
                    .chatResponse();
            return new Answer(
                    Objects.requireNonNullElse(reply.getResult().getOutput().getText(), ""),
                    reply.hasFinishReasons(Set.of(FINISH_REASON)),
                    calls);
        } catch (RuntimeException e) {
            String reason = NestedExceptionUtils.getMostSpecificCause(e).toString();
            log.warn("The model failed: {}", reason);
            throw new ResponseStatusException(BAD_GATEWAY, "The model failed: " + reason, e);
        }
    }

    private static void send(SseEmitter stream, String event, Object data) {
        try {
            stream.send(SseEmitter.event().name(event).data(data, APPLICATION_JSON));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
