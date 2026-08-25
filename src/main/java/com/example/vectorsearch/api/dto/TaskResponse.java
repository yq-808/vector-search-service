package com.example.vectorsearch.api.dto;

import com.example.vectorsearch.document.Document;
import com.example.vectorsearch.task.TaskStatus;
import com.example.vectorsearch.task.VectorizationTask;

import java.time.Instant;

/** Status of a vectorisation task, with the document it produced once it is available. */
public record TaskResponse(String taskId,
                           String documentId,
                           TaskStatus status,
                           String errorMessage,
                           Instant createdAt,
                           Instant startedAt,
                           Instant finishedAt,
                           DocumentResponse document) {

    public static TaskResponse from(VectorizationTask task, Document document) {
        return new TaskResponse(
                task.getId(),
                task.getDocumentId(),
                task.getStatus(),
                task.getErrorMessage(),
                task.getCreatedAt(),
                task.getStartedAt(),
                task.getFinishedAt(),
                document == null ? null : DocumentResponse.from(document));
    }
}
