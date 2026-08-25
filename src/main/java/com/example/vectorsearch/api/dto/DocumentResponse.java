package com.example.vectorsearch.api.dto;

import com.example.vectorsearch.document.Document;
import com.example.vectorsearch.document.DocumentStatus;

import java.time.Instant;

/** A document and its full provenance record. */
public record DocumentResponse(String documentId,
                               String channel,
                               String content,
                               DocumentStatus status,
                               boolean vectorReady,
                               long hitCount,
                               Instant submittedAt,
                               Instant vectorizedAt) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getChannel(),
                document.getContent(),
                document.getStatus(),
                document.isVectorReady(),
                document.getHitCount(),
                document.getSubmittedAt(),
                document.getVectorizedAt());
    }
}
