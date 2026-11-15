package com.vector.airport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

/**
 * The app as a reviewer starts it without an API key, here on the fixture data with its real servers: the data endpoint
 * answers on the local management port, the chat answers 503 saying how to set a model, and the MCP server offers every
 * tool.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"spring.ai.model.chat=none", "management.server.port=0", "spring.ai.mcp.server.enabled=true"})
@ActiveProfiles({"us", "fixtures"})
class AppTest {

    @LocalServerPort
    int port;

    @LocalManagementPort
    int managementPort;

    @Test
    void dataEndpointListsWhatEachSourceLoaded() {
        List<Map<String, Object>> sources = RestClient.create("http://127.0.0.1:" + managementPort)
                .get()
                .uri("/actuator/data")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        assertThat(sources).isNotEmpty().allSatisfy(source -> assertThat(source).containsEntry("matches_lock", true));
    }

    @Test
    void chatWithoutAModelAnswers503SayingHowToSetOne() {
        assertThatThrownBy(() -> RestClient.create("http://127.0.0.1:" + port)
                        .post()
                        .uri("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("conversation", UUID.randomUUID(), "text", "Which airports grow fastest?"))
                        .retrieve()
                        .toBodilessEntity())
                .isInstanceOfSatisfying(
                        HttpServerErrorException.ServiceUnavailable.class,
                        unavailable -> assertThat(unavailable.getResponseBodyAs(Map.class))
                                .extractingByKey("detail")
                                .asString()
                                .startsWith("No LLM: restart with LLM_PROVIDER"));
    }

    @Test
    void mcpServerOffersEveryToolTheChatHas(@Autowired List<SyncToolSpecification> tools) {
        assertThat(tools)
                .extracting(tool -> tool.tool().name())
                .containsExactlyInAnyOrder("find", "traffic", "longHaul", "congestion", "growth", "runways", "rank");
    }
}
