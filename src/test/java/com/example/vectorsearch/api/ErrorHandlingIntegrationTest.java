package com.example.vectorsearch.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The README promises that errors come back in one shape. These drive the failures the framework
 * raises before a controller is ever reached, which is where that promise is easiest to break.
 */
class ErrorHandlingIntegrationTest extends ApiIntegrationTest {

    @Test
    void reportsAMalformedBodyInTheStandardShape() {
        ResponseEntity<JsonNode> response = post("/api/v1/search", "{not json");

        assertStandardShape(response, HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message").asText()).isEqualTo("malformed request body");
    }

    @Test
    void reportsAnUnsupportedMethodInTheStandardShape() {
        assertStandardShape(
                rest.exchange("/api/v1/search", HttpMethod.GET, null, JsonNode.class),
                HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void reportsAnUnparseableQueryParameterInTheStandardShape() {
        ResponseEntity<JsonNode> response =
                rest.getForEntity("/api/v1/documents?status=NOT_A_STATUS", JsonNode.class);

        assertStandardShape(response, HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message").asText())
                .contains("status")
                .contains("NOT_A_STATUS")
                .contains("ACTIVE");
    }

    @Test
    void reportsAnUnknownPathWithoutMentioningStaticResources() {
        ResponseEntity<JsonNode> response = rest.getForEntity("/api/v1/nope", JsonNode.class);

        assertStandardShape(response, HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("message").asText())
                .isEqualTo("no endpoint for GET /api/v1/nope");
    }

    @Test
    void rejectsADocumentIdThatCouldNotBeAddressedInAUrl() {
        ResponseEntity<JsonNode> response = rest.postForEntity(
                "/api/v1/documents", Map.of("documentId", "bad/id", "content", "text"), JsonNode.class);

        assertStandardShape(response, HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message").asText()).contains("documentId");
    }

    @Test
    void rejectsAPageSizeBeyondTheAllowedRange() {
        ResponseEntity<JsonNode> response = rest.getForEntity("/api/v1/documents?size=5000", JsonNode.class);

        assertStandardShape(response, HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message").asText()).contains("size");
    }

    private ResponseEntity<JsonNode> post(String path, String rawBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity(path, new HttpEntity<>(rawBody, headers), JsonNode.class);
    }

    /** Our body, not the container's: {@code timestamp/status/error/message} and never {@code path}. */
    private static void assertStandardShape(ResponseEntity<JsonNode> response, HttpStatus expected) {
        assertThat(response.getStatusCode()).isEqualTo(expected);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.has("timestamp")).isTrue();
        assertThat(body.get("status").asInt()).isEqualTo(expected.value());
        assertThat(body.get("error").asText()).isEqualTo(expected.getReasonPhrase());
        assertThat(body.has("message")).isTrue();
        assertThat(body.has("path")).isFalse();
    }
}
