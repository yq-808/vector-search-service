package com.example.vectorsearch.api;

import com.example.vectorsearch.api.dto.DocumentResponse;
import com.example.vectorsearch.api.dto.SearchResponse;
import com.example.vectorsearch.api.dto.SubmitDocumentResponse;
import com.example.vectorsearch.api.dto.TaskResponse;
import com.example.vectorsearch.task.TaskStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for the black-box tests: they drive the running service over HTTP only, and never
 * reach into a bean or the database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class ApiIntegrationTest {

    private static final Duration TASK_TIMEOUT = Duration.ofSeconds(20);
    private static final long POLL_INTERVAL_MILLIS = 20;

    @Autowired
    protected TestRestTemplate rest;

    protected SubmitDocumentResponse submit(String documentId, String content, String channel) {
        ResponseEntity<SubmitDocumentResponse> response = rest.postForEntity(
                "/api/v1/documents", submission(documentId, content, channel), SubmitDocumentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    /** Submits a document and returns only once it is vectorised and therefore searchable. */
    protected DocumentResponse ingest(String documentId, String content, String channel) {
        TaskResponse task = awaitCompletion(submit(documentId, content, channel).taskId());

        assertThat(task.status()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(task.document().vectorReady()).isTrue();
        return task.document();
    }

    protected TaskResponse awaitCompletion(String taskId) {
        Instant deadline = Instant.now().plus(TASK_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            TaskResponse task = task(taskId);
            if (task != null && !TaskStatus.IN_FLIGHT.contains(task.status())) {
                return task;
            }
            pause();
        }
        throw new AssertionError("task " + taskId + " never reached a terminal state");
    }

    protected TaskResponse task(String taskId) {
        return rest.getForObject("/api/v1/tasks/{taskId}", TaskResponse.class, taskId);
    }

    protected DocumentResponse document(String documentId) {
        return rest.getForObject("/api/v1/documents/{documentId}", DocumentResponse.class, documentId);
    }

    protected SearchResponse search(String query, Integer topK, String channel) {
        return rest.postForObject("/api/v1/search", searchRequest(query, topK, channel), SearchResponse.class);
    }

    protected static Map<String, Object> submission(String documentId, String content, String channel) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("documentId", documentId);
        body.put("content", content);
        if (channel != null) {
            body.put("channel", channel);
        }
        return body;
    }

    protected static Map<String, Object> searchRequest(String query, Integer topK, String channel) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        if (topK != null) {
            body.put("topK", topK);
        }
        if (channel != null) {
            body.put("channel", channel);
        }
        return body;
    }

    private static void pause() {
        try {
            Thread.sleep(POLL_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
