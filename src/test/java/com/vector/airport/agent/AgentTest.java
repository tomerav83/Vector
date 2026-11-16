package com.vector.airport.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vector.airport.FixtureDataTest;
import com.vector.airport.agent.call.Call;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The agent offers the model every tool, answers its tool calls from the data, keeps each conversation apart, and says
 * why it cannot answer: an invalid question, a failing model. Without a model: {@code AppTest}.
 */
@FixtureDataTest
@ExtendWith(OutputCaptureExtension.class)
class AgentTest {

    @Autowired
    ScriptedModel model;

    @Autowired
    Agent agent;

    @Value("${spring.ai.tools.limits.max-total-tool-calls}")
    int maxToolCalls;

    @Autowired
    WebApplicationContext context;

    final List<String> steps = new ArrayList<>();

    @BeforeEach
    void forgetScript() {
        model.reset();
    }

    @Test
    void modelGetsEveryToolAndTheFiguresOfTheOneItCalls() {
        model.replies(call("traffic", "{\"airports\": [\"SFO\"]}"), answer("SFO's flights are 81% full."));

        Answer answer = ask(question("How full are SFO's flights?"));

        assertThat(answer.text()).isEqualTo("SFO's flights are 81% full.");
        assertThat(tools(model.prompts.getFirst()))
                .containsExactlyInAnyOrder(
                        "find",
                        "count",
                        "traffic",
                        "longHaul",
                        "congestion",
                        "growth",
                        "runways",
                        "flightLimits",
                        "rank",
                        "clarify");
        assertThat(toolResult(model.prompts.getLast())).contains("\"load_factor\":0.8095");
        assertThat(answer.calls()).singleElement().satisfies(call -> {
            assertThat(call.tool()).isEqualTo("traffic");
            assertThat(call.evidence()).isEqualTo(toolResult(model.prompts.getLast()));
        });
    }

    @Test
    void eachToolCallIsReportedAsAStepBeforeTheAnswer() {
        model.replies(
                call("find", "{\"query\": \"San Francisco\"}"),
                call("traffic", "{\"airports\": [\"SFO\"]}"),
                answer("SFO's flights are 81% full."));

        ask(question("How full are San Francisco's flights?"));

        assertThat(steps).containsExactly("find", "traffic");
    }

    @Test
    void geminiAnswerInPartsIsJoined() {
        model.parts("SFO's flights are ", "81% full.");

        assertThat(ask(question("How full are SFO's flights?")).text()).isEqualTo("SFO's flights are 81% full.");
    }

    @Test
    void followUpIsAskedWithTheConversationSoFar() {
        model.replies(answer("Logan is BOS."), answer("BOS is a large hub."));
        UUID logan = UUID.randomUUID();

        ask(new Question(logan, "Which airport is Logan?"));
        ask(new Question(logan, "How big is it?"));

        assertThat(model.prompts.getLast().getInstructions())
                .extracting(Message::getText)
                .containsSubsequence("Which airport is Logan?", "Logan is BOS.", "How big is it?");
    }

    @Test
    void otherConversationDoesNotSeeThisOne() {
        model.replies(answer("Logan is BOS."), answer("Which airport do you mean?"));

        ask(question("Which airport is Logan?"));
        ask(question("How big is it?"));

        assertThat(model.prompts.getLast().getInstructions())
                .extracting(Message::getText)
                .doesNotContain("Which airport is Logan?");
    }

    @Test
    void clarifyEndsTheTurnWithItsQuestionMarkedForThePage() {
        model.replies(call("clarify", "{\"question\": \"Portland, Maine (PWM) or Oregon (PDX)?\"}"));

        Answer answer = ask(question("How busy is Portland?"));

        assertThat(answer).isEqualTo(new Answer("Portland, Maine (PWM) or Oregon (PDX)?", true, List.of()));
        assertThat(model.prompts).hasSize(1);
    }

    @Test
    void followUpToAClarifyingQuestionIsAskedWithIt() {
        model.replies(
                call("clarify", "{\"question\": \"Portland, Maine (PWM) or Oregon (PDX)?\"}"),
                answer("PWM is in Maine."));
        UUID portland = UUID.randomUUID();

        ask(new Question(portland, "How busy is Portland?"));
        Answer answer = ask(new Question(portland, "Maine"));

        assertThat(answer.clarifying()).isFalse();
        assertThat(model.prompts.getLast().getInstructions())
                .extracting(Message::getText)
                .containsSubsequence("How busy is Portland?", "Portland, Maine (PWM) or Oregon (PDX)?", "Maine");
    }

    @Test
    void figureNoToolReturnedIsAskedAgainOnceAndOnlyTheFinalAnswerIsRemembered() {
        model.replies(
                call("traffic", "{\"airports\": [\"SFO\"]}"),
                answer("SFO's flights are 90% full."),
                answer("SFO's flights are 81% full."),
                answer("BOS is a large hub."));
        UUID sfo = UUID.randomUUID();

        Answer answer = ask(new Question(sfo, "How full are SFO's flights?"));
        ask(new Question(sfo, "And BOS?"));

        assertThat(answer.text()).isEqualTo("SFO's flights are 81% full.");
        assertThat(model.prompts.get(2).getInstructions().getLast().getText())
                .isEqualTo("These figures are in no tool result of this answer: 90%. Call the tool that has them,"
                        + " or drop them.");
        assertThat(model.prompts.getLast().getInstructions())
                .extracting(Message::getText)
                .containsSubsequence("How full are SFO's flights?", "SFO's flights are 81% full.", "And BOS?")
                .noneMatch(text -> text.contains("90%"));
    }

    @Test
    void figureStillStrayAfterTheRetryIsCutWithItsSentenceAndLogged(CapturedOutput log) {
        String stray = "**SFO's flights are 81% full.**\n- It flew 3,348,342 seats.";
        model.replies(call("traffic", "{\"airports\": [\"SFO\"]}"), answer(stray), answer(stray));

        Answer answer = ask(question("How full are SFO's flights?"));

        assertThat(answer.text()).isEqualTo("**SFO's flights are 81% full.**");
        assertThat(answer.calls()).extracting(Call::tool).containsExactly("traffic");
        assertThat(log).contains("Cut the sentences with figures no tool returned, after a retry: [3,348,342]");
    }

    @Test
    void toolFailureGoesBackToTheModelAsJsonAndIntoTheLog(CapturedOutput log) {
        model.replies(call("traffic", "{\"airports\": [\"LA\"]}"), answer("Which LA do you mean?"));

        Answer answer = ask(question("How full are LA's flights?"));

        assertThat(toolResult(model.prompts.getLast()))
                .isEqualTo("{\"error\":\"No airport has the code [LA]; look it up with find\"}");
        assertThat(answer.calls()).isEmpty();
        assertThat(log).contains("Tool traffic failed: No airport has the code [LA]");
    }

    @Test
    void modelThatKeepsCallingToolsIsStoppedAtTheLimit() {
        IntStream.rangeClosed(0, maxToolCalls).forEach(_ -> model.replies(call("find", "{\"query\": \"Logan\"}")));

        Answer answer = ask(question("Which airport is Logan?"));

        assertThat(answer.text()).contains("Total tool call limit (%d) exceeded".formatted(maxToolCalls));
        assertThat(model.prompts).hasSize(maxToolCalls + 1);
    }

    @Test
    void modelFailureAnswers502WithTheProvidersReason(CapturedOutput log) {
        model.fails(new RuntimeException("Failed to generate content", new IOException("401 API key not valid")));

        assertThatThrownBy(() -> ask(question("Which airports grow fastest?")))
                .isInstanceOfSatisfying(ResponseStatusException.class, failed -> {
                    assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(failed.getReason())
                            .isEqualTo("The model failed: java.io.IOException: 401 API key not valid");
                });
        assertThat(log).containsOnlyOnce("The model failed: java.io.IOException: 401 API key not valid");
    }

    @Test
    void pageGetsEachStepThenTheAnswerWithTheToolsRowsAsJson() {
        model.replies(call("traffic", "{\"airports\": [\"SFO\"]}"), answer("**SFO's flights are 81% full.**"));

        MvcTestResult result = chat(Map.of("conversation", UUID.randomUUID(), "text", "How full are SFO's flights?"));

        assertThat(result).hasStatusOk();
        assertThat(events(result)).containsKeys("step", "answer").containsEntry("step", "traffic");
        JsonNode answer = JsonMapper.shared().readTree(events(result).get("answer"));
        assertThat(answer.get("text").asString()).isEqualTo("**SFO's flights are 81% full.**");
        assertThat(answer.get("clarifying").asBoolean()).isFalse();
        assertThat(answer.at("/calls/0/tool").asString()).isEqualTo("traffic");
        assertThat(answer.at("/calls/0/evidence/rows/0/load_factor").asDouble()).isEqualTo(0.8095);
    }

    @Test
    void modelFailureReachesThePageAsAnErrorEventWithTheProvidersReason() {
        model.fails(new RuntimeException("Failed to generate content", new IOException("401 API key not valid")));

        MvcTestResult result = chat(Map.of("conversation", UUID.randomUUID(), "text", "Which airports grow fastest?"));

        JsonNode error = JsonMapper.shared().readTree(events(result).get("error"));
        assertThat(error.get("status").asInt()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
        assertThat(error.get("detail").asString())
                .isEqualTo("The model failed: java.io.IOException: 401 API key not valid");
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

    private static AssistantMessage call(String tool, String arguments) {
        return AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall("1", "function", tool, arguments)))
                .build();
    }

    private static AssistantMessage answer(String text) {
        return AssistantMessage.builder().content(text).build();
    }

    private Answer ask(Question question) {
        return agent.answer(question, steps::add);
    }

    private static Question question(String text) {
        return new Question(UUID.randomUUID(), text);
    }

    private static List<String> tools(Prompt prompt) {
        return ((ToolCallingChatOptions) prompt.getOptions())
                .getToolCallbacks().stream()
                        .map(tool -> tool.getToolDefinition().name())
                        .toList();
    }

    /** What the tool the model called answered, as the model reads it. */
    private static String toolResult(Prompt prompt) {
        return ((ToolResponseMessage) prompt.getInstructions().getLast())
                .getResponses()
                .getFirst()
                .responseData();
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

    /** Each server-sent event of {@code result} by its name, its data as sent. */
    private static Map<String, String> events(MvcTestResult result) {
        return Arrays.stream(
                        new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8).split("\n\n"))
                .map(event -> event.lines()
                        .collect(Collectors.toMap(
                                line -> line.substring(0, line.indexOf(':')),
                                line -> line.substring(line.indexOf(':') + 1))))
                .collect(Collectors.toMap(event -> event.get("event"), event -> event.get("data")));
    }
}
