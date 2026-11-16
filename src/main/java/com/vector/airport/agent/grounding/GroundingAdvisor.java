package com.vector.airport.agent.grounding;

import static java.util.stream.Collectors.joining;
import static org.springframework.ai.model.tool.ToolExecutionResult.FINISH_REASON;

import com.vector.airport.agent.call.Call;
import com.vector.airport.agent.call.Shown;
import com.vector.airport.agent.clarification.Clarification;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/**
 * Keeps an answer to the figures of its {@link Grounding}: when the model writes one no tool returned, it is asked once
 * more in the same turn, naming the stray figures; when it still writes one, the sentences that hold it are cut and
 * logged. It sits inside the chat memory's advisor, so the memory keeps only the question and the final answer.
 *
 * <p>The answer leaves as one generation: Gemini answers in several parts, which it joins. A question back
 * ({@link Clarification}) passes as it is.
 */
public class GroundingAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(GroundingAdvisor.class);

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse draft = chain.nextCall(request);
        if (draft.chatResponse().hasFinishReasons(Set.of(FINISH_REASON))) {
            return draft;
        }
        List<String> stray = grounding(request).stray(text(draft));
        if (stray.isEmpty()) {
            return answer(draft, text(draft));
        }
        ChatClientResponse retried = chain.copy(this).nextCall(retry(request, text(draft), stray));
        Grounding grounding = grounding(request);
        List<String> left = grounding.stray(text(retried));
        if (!left.isEmpty()) {
            log.warn("Cut the sentences with figures no tool returned, after a retry: {}", left);
        }
        return answer(retried, grounding.cut(text(retried)));
    }

    /** Inside the chat memory's advisor, which runs first. */
    @Override
    public int getOrder() {
        return Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER + 1;
    }

    @Override
    public String getName() {
        return GroundingAdvisor.class.getSimpleName();
    }

    /**
     * The numbers of the prompt (system prompt, conversation, question), the tool descriptions and this turn's calls.
     */
    @SuppressWarnings("unchecked")
    private static Grounding grounding(ChatClientRequest request) {
        ToolCallingChatOptions options =
                (ToolCallingChatOptions) request.prompt().getOptions();
        List<Call> calls = (List<Call>) options.getToolContext().get(Shown.CALLS);
        return Grounding.create(Stream.of(
                        request.prompt().getInstructions().stream().map(Message::getText),
                        options.getToolCallbacks().stream()
                                .map(tool -> tool.getToolDefinition().description()),
                        calls.stream().map(Call::evidence))
                .flatMap(texts -> texts)
                .filter(Objects::nonNull));
    }

    private static String text(ChatClientResponse response) {
        return response.chatResponse().getResults().stream()
                .map(part -> part.getOutput().getText())
                .filter(Objects::nonNull)
                .collect(joining());
    }

    /** {@code response} with {@code text} as its one generation, keeping its metadata; as it is without one. */
    private static ChatClientResponse answer(ChatClientResponse response, String text) {
        Generation first = response.chatResponse().getResult();
        if (first == null) {
            return response;
        }
        return response.mutate()
                .chatResponse(ChatResponse.builder()
                        .from(response.chatResponse())
                        .generations(List.of(new Generation(
                                AssistantMessage.builder().content(text).build(), first.getMetadata())))
                        .build())
                .build();
    }

    /** The request again with the draft and a note naming its stray figures, e.g. 3,348,342 seats. */
    private static ChatClientRequest retry(ChatClientRequest request, String draft, List<String> stray) {
        List<Message> messages = Stream.concat(
                        request.prompt().getInstructions().stream(),
                        Stream.of(
                                new AssistantMessage(draft),
                                new UserMessage(
                                        "These figures are in no tool result of this answer: %s. Call the tool that has them, or drop them."
                                                .formatted(String.join(", ", stray)))))
                .toList();
        return request.mutate()
                .prompt(request.prompt()
                        .mutate()
                        .messages(messages)
                        .chatOptions(request.prompt().getOptions())
                        .build())
                .build();
    }
}
