package com.example.vectorsearch.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * One unit of asynchronous work: vectorise the current content of a document.
 *
 * <p>Tasks are the durable record of the queue. State transitions are performed with conditional
 * updates in {@link VectorizationTaskRepository} rather than by mutating a loaded entity, so two
 * workers can never process the same task.
 */
@Entity
@Table(name = "vectorization_task")
public class VectorizationTask {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "document_id", length = 64, nullable = false)
    private String documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private TaskStatus status = TaskStatus.QUEUED;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected VectorizationTask() {
        // for JPA
    }

    public VectorizationTask(String id, String documentId, Instant createdAt) {
        this.id = id;
        this.documentId = documentId;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getDocumentId() {
        return documentId;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
