package com.vector.airport.agent;

import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/**
 * A chat model that answers from a script and keeps every prompt it gets, so a test runs the agent without an LLM; each
 * {@code @FixtureDataTest} app has one.
 */
public class ScriptedModel implements ChatModel {

    final List<Prompt> prompts = new CopyOnWriteArrayList<>();

    private final Queue<Supplier<AssistantMessage>> replies = new ConcurrentLinkedQueue<>();

    /** Its answers to the next prompts, in order; the prompts and replies of the test before are forgotten. */
    void replies(AssistantMessage... next) {
        Stream.of(next).forEach(reply -> replies.add(() -> reply));
    }

    /** Fails the next prompt the way a provider does, e.g. on a wrong API key. */
    void fails(RuntimeException failure) {
        replies.add(() -> {
            throw failure;
        });
    }

    /** Forgets the prompts and any replies a test left unused. */
    void reset() {
        prompts.clear();
        replies.clear();
    }

    /** @throws AssertionError naming the question when the script has no reply left */
    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        Supplier<AssistantMessage> reply = Optional.ofNullable(replies.poll())
                .orElseThrow(() -> new AssertionError(
                        "No scripted reply left for: " + prompt.getUserMessage().getText()));
        return new ChatResponse(List.of(new Generation(reply.get())));
    }

    /** Tool-calling options, the only kind the chat client hands tools to. */
    @Override
    public ChatOptions getOptions() {
        return ToolCallingChatOptions.builder().build();
    }
}
