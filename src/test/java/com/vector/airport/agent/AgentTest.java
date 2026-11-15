package com.vector.airport.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vector.airport.FixtureDataTest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The agent offers the model every tool, answers its tool calls from the data, keeps each conversation apart, and says
 * why it cannot answer: an invalid question, a failing model. Without a model: {@code AppTest}.
 */
@FixtureDataTest
@ExtendWith(OutputCaptureExtension.class)
class AgentTest {

    @Autowired
    Agent agent;

    @Autowired
    ScriptedModel model;

    @Autowired
    WebApplicationContext context;

    @Value("${spring.ai.tools.limits.max-total-tool-calls}")
    int maxToolCalls;

    @BeforeEach
    void forgetScript() {
        model.reset();
    }

    @Test
    void modelGetsEveryToolAndTheFiguresOfTheOneItCalls() {
        model.replies(call("traffic", "{\"airports\": [\"SFO\"]}"), answer("SFO's flights are 81% full."));

        Answer answer = agent.answer(question("How full are SFO's flights?"));

        assertThat(answer.text()).isEqualTo("SFO's flights are 81% full.");
        assertThat(tools(model.prompts.getFirst()))
                .containsExactlyInAnyOrder("find", "traffic", "longHaul", "congestion", "growth", "runways", "rank");
        assertThat(toolResult(model.prompts.getLast())).contains("\"load_factor\":0.8095");
    }

    @Test
    void followUpIsAskedWithTheConversationSoFar() {
        UUID logan = UUID.randomUUID();
        model.replies(answer("Logan is BOS."), answer("BOS is a large hub."));

        agent.answer(new Question(logan, "Which airport is Logan?"));
        agent.answer(new Question(logan, "How big is it?"));

        assertThat(model.prompts.getLast().getInstructions())
                .extracting(Message::getText)
                .containsSubsequence("Which airport is Logan?", "Logan is BOS.", "How big is it?");
    }

    @Test
    void otherConversationDoesNotSeeThisOne() {
        model.replies(answer("Logan is BOS."), answer("Which airport do you mean?"));

        agent.answer(question("Which airport is Logan?"));
        agent.answer(question("How big is it?"));

        assertThat(model.prompts.getLast().getInstructions())
                .extracting(Message::getText)
                .doesNotContain("Which airport is Logan?");
    }

    @Test
    void toolFailureGoesBackToTheModelAndIntoTheLog(CapturedOutput log) {
        model.replies(call("traffic", "{\"airports\": [\"LA\"]}"), answer("Which LA do you mean?"));

        agent.answer(question("How full are LA's flights?"));

        assertThat(toolResult(model.prompts.getLast())).isEqualTo("No airport has the code [LA]; look it up with find");
        assertThat(log).contains("Exception thrown by tool: traffic");
    }

    @Test
    void modelThatKeepsCallingToolsIsStoppedAtTheLimit() {
        IntStream.rangeClosed(0, maxToolCalls).forEach(_ -> model.replies(call("find", "{\"query\": \"Logan\"}")));

        Answer answer = agent.answer(question("Which airport is Logan?"));

        assertThat(answer.text()).contains("Total tool call limit (%d) exceeded".formatted(maxToolCalls));
        assertThat(model.prompts).hasSize(maxToolCalls + 1);
    }

    @Test
    void modelFailureAnswers502WithTheProvidersReason(CapturedOutput log) {
        model.fails(new RuntimeException("Failed to generate content", new IOException("401 API key not valid")));

        assertThatThrownBy(() -> agent.answer(question("Which airports grow fastest?")))
                .isInstanceOfSatisfying(ResponseStatusException.class, failed -> {
                    assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(failed.getReason())
                            .isEqualTo("The model failed: java.io.IOException: 401 API key not valid");
                });
        assertThat(log).containsOnlyOnce("The model failed: java.io.IOException: 401 API key not valid");
    }

    @Test
    void answerIsJsonForThePage() {
        model.replies(answer("BOS is a large hub."));

        assertThat(chat(Map.of("conversation", UUID.randomUUID(), "text", "How big is BOS?")))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.text")
                .isEqualTo("BOS is a large hub.");
    }

    @Test
    void invalidQuestionAnswers400SayingWhy() {
        assertThat(chat(Map.of("conversation", UUID.randomUUID(), "text", " ")))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("text is required: the question");
        assertThat(chat(Map.of("conversation", UUID.randomUUID(), "text", "?".repeat(Question.MAX_TEXT + 1))))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("text is over %d characters".formatted(Question.MAX_TEXT));
        assertThat(chat(Map.of("text", "How big is BOS?")))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("conversation is required: the chat's id, a UUID");
        assertThat(model.prompts).isEmpty();
    }

    /** {@code POST /api/chat} through Spring MVC, as the page sends it. */
    private MvcTestResult chat(Map<String, Object> body) {
        return MockMvcTester.from(context)
                .post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.shared().writeValueAsString(body))
                .exchange();
    }

    private static Question question(String text) {
        return new Question(UUID.randomUUID(), text);
    }

    private static AssistantMessage call(String tool, String arguments) {
        return AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall("1", "function", tool, arguments)))
                .build();
    }

    private static AssistantMessage answer(String text) {
        return AssistantMessage.builder().content(text).build();
    }

    /** What the tool the model called answered, as the model reads it. */
    private static String toolResult(Prompt prompt) {
        return ((ToolResponseMessage) prompt.getInstructions().getLast())
                .getResponses()
                .getFirst()
                .responseData();
    }

    private static List<String> tools(Prompt prompt) {
        return ((ToolCallingChatOptions) prompt.getOptions())
                .getToolCallbacks().stream()
                        .map(tool -> tool.getToolDefinition().name())
                        .toList();
    }
}
