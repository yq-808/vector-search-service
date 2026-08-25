package com.example.vectorsearch.api.dto;

import com.example.vectorsearch.task.TaskStatus;

/** Acknowledgement of an accepted submission; the work itself has only been queued. */
public record SubmitDocumentResponse(String taskId, String documentId, TaskStatus status) {

    public static SubmitDocumentResponse accepted(String taskId, String documentId) {
        return new SubmitDocumentResponse(taskId, documentId, TaskStatus.QUEUED);
    }
}
