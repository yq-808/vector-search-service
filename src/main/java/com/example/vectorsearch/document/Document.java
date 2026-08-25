package com.example.vectorsearch.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * A document plus everything needed to trace it: where it came from, when it was ingested,
 * when its vector became available, and how often retrieval has surfaced it.
 */
@Entity
@Table(name = "document")
public class Document {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "channel", length = 64, nullable = false)
    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private DocumentStatus status = DocumentStatus.ACTIVE;

    @Column(name = "vector_ready", nullable = false)
    private boolean vectorReady;

    @Column(name = "embedding")
    private byte[] embedding;

    /**
     * Written only by {@link DocumentRepository#incrementHitCounts}, an atomic SQL increment.
     * Marked non-updatable so an ordinary entity flush can never write back a stale count.
     */
    @Column(name = "hit_count", nullable = false, updatable = false)
    private long hitCount;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "vectorized_at")
    private Instant vectorizedAt;

    /** Optimistic lock guarding concurrent updates to the same document. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Document() {
        // for JPA
    }

    public Document(String id, String content, String channel, Instant submittedAt) {
        this.id = id;
        this.content = content;
        this.channel = channel;
        this.submittedAt = submittedAt;
    }

    /** Accepts new content: the previous vector no longer describes this document. */
    public void resubmit(String content, String channel, Instant submittedAt) {
        this.content = content;
        this.channel = channel;
        this.status = DocumentStatus.ACTIVE;
        this.submittedAt = submittedAt;
        this.vectorReady = false;
        this.embedding = null;
        this.vectorizedAt = null;
    }

    public void applyVector(byte[] embedding, Instant vectorizedAt) {
        this.embedding = embedding;
        this.vectorizedAt = vectorizedAt;
        this.vectorReady = true;
    }

    public void invalidate() {
        this.status = DocumentStatus.INVALID;
    }

    public String getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public String getChannel() {
        return channel;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public boolean isVectorReady() {
        return vectorReady;
    }

    public byte[] getEmbedding() {
        return embedding;
    }

    public long getHitCount() {
        return hitCount;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getVectorizedAt() {
        return vectorizedAt;
    }
}
