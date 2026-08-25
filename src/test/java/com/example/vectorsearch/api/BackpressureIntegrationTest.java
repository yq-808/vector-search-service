package com.example.vectorsearch.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The queue is bounded on purpose. With one slow worker and room for a single task, submissions
 * must be refused with 503 rather than absorbed into unbounded heap usage.
 */
@TestPropertySource(properties = {
        "vector.vectorization.workers=1",
        "vector.vectorization.queue-capacity=1",
        "vector.vectorization.simulated-cost-millis=3000",
        "spring.datasource.url=jdbc:h2:mem:vector-search-backpressure;DB_CLOSE_DELAY=-1"
})
class BackpressureIntegrationTest extends ApiIntegrationTest {

    @Test
    void refusesSubmissionsOnceTheBacklogIsFull() {
        List<ResponseEntity<JsonNode>> responses = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            responses.add(rest.postForEntity("/api/v1/documents",
                    submission("backpressure-" + i, "content " + i, "backpressure"), JsonNode.class));
        }

        ResponseEntity<JsonNode> refused = responses.stream()
                .filter(response -> response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE)
                .findFirst()
                .orElseThrow(() -> new AssertionError("a full queue never produced a 503"));

        assertThat(refused.getBody().get("message").asText()).contains("queue is full");
        assertThat(responses.get(0).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }
}
