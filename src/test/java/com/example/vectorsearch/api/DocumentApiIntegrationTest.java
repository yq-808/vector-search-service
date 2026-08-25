package com.example.vectorsearch.api;

import com.example.vectorsearch.api.dto.DocumentResponse;
import com.example.vectorsearch.api.dto.ErrorResponse;
import com.example.vectorsearch.api.dto.SubmitDocumentResponse;
import com.example.vectorsearch.api.dto.TaskResponse;
import com.example.vectorsearch.document.DocumentStatus;
import com.example.vectorsearch.task.TaskStatus;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The document lifecycle, driven entirely through the HTTP API. */
class DocumentApiIntegrationTest extends ApiIntegrationTest {

    @Test
    void acceptsASubmissionWithoutDoingTheWorkYet() {
        SubmitDocumentResponse accepted = submit("doc-accept", "asynchronous ingestion", "docs");

        assertThat(accepted.taskId()).isNotBlank();
        assertThat(accepted.documentId()).isEqualTo("doc-accept");
        assertThat(accepted.status()).isEqualTo(TaskStatus.QUEUED);
    }

    @Test
    void recordsProvenanceOnceTheTaskCompletes() {
        DocumentResponse document = ingest("doc-ready", "vectorisation finished", "docs");

        assertThat(document.vectorReady()).isTrue();
        assertThat(document.status()).isEqualTo(DocumentStatus.ACTIVE);
        assertThat(document.submittedAt()).isNotNull();
        assertThat(document.vectorizedAt()).isNotNull();
        assertThat(document.hitCount()).isZero();
    }

    @Test
    void fallsBackToTheDefaultChannel() {
        assertThat(ingest("doc-no-channel", "no channel supplied", null).channel()).isEqualTo("default");
    }

    @Test
    void acceptsEmptyContent() {
        DocumentResponse document = ingest("doc-empty", "", "docs");

        assertThat(document.vectorReady()).isTrue();
        assertThat(document.content()).isEmpty();
    }

    @Test
    void invalidatesADocumentOnRequest() {
        ingest("doc-invalidate", "retire me", "docs");

        DocumentResponse invalidated = rest.postForObject(
                "/api/v1/documents/{id}/invalidate", null, DocumentResponse.class, "doc-invalidate");

        assertThat(invalidated.status()).isEqualTo(DocumentStatus.INVALID);
        assertThat(document("doc-invalidate").status()).isEqualTo(DocumentStatus.INVALID);
    }

    @Test
    void listsDocumentsOfOneChannel() {
        ingest("doc-list-1", "first listed document", "listing");
        ingest("doc-list-2", "second listed document", "listing");

        JsonNode page = rest.getForObject("/api/v1/documents?channel=listing&size=50", JsonNode.class);

        assertThat(page.get("totalElements").asLong()).isEqualTo(2);
        assertThat(page.get("items").findValuesAsText("documentId")).containsExactlyInAnyOrder("doc-list-1", "doc-list-2");
    }

    @Test
    void exposesTheLatestTaskOfADocument() {
        String taskId = submit("doc-latest-task", "which task made this?", "docs").taskId();
        awaitCompletion(taskId);

        TaskResponse latest = rest.getForObject(
                "/api/v1/documents/{id}/task", TaskResponse.class, "doc-latest-task");

        assertThat(latest.taskId()).isEqualTo(taskId);
        assertThat(latest.status()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(latest.finishedAt()).isNotNull();
    }

    @Test
    void keepsTheHitCountWhenADocumentIsResubmitted() {
        ingest("doc-resubmit", "original content about kayaks", "resubmit");
        search("kayaks", 5, "resubmit");
        assertThat(document("doc-resubmit").hitCount()).isEqualTo(1);

        ingest("doc-resubmit", "replacement content about canoes", "resubmit");

        DocumentResponse document = document("doc-resubmit");
        assertThat(document.content()).isEqualTo("replacement content about canoes");
        assertThat(document.hitCount()).isEqualTo(1);
        assertThat(document.vectorReady()).isTrue();
    }

    @Test
    void reportsUnknownDocumentsAndTasksAsNotFound() {
        assertThat(rest.getForEntity("/api/v1/documents/{id}", ErrorResponse.class, "missing").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/api/v1/tasks/{id}", ErrorResponse.class, "missing").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsASubmissionWithoutADocumentId() {
        ResponseEntity<ErrorResponse> response = rest.postForEntity(
                "/api/v1/documents", Map.of("content", "orphan"), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("documentId");
    }
}
