package com.example.vectorsearch.document;

import com.example.vectorsearch.vectorization.VectorizationService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/** Ingestion and lifecycle of documents. Vectorisation itself happens asynchronously. */
@Service
public class DocumentService {

    public static final String DEFAULT_CHANNEL = "default";

    private final DocumentRepository documentRepository;
    private final VectorizationService vectorizationService;

    public DocumentService(DocumentRepository documentRepository,
                           VectorizationService vectorizationService) {
        this.documentRepository = documentRepository;
        this.vectorizationService = vectorizationService;
    }

    /**
     * Stores (or replaces) a document and queues it for vectorisation.
     *
     * <p>Returns as soon as the row is committed; the document only becomes searchable once a
     * worker finishes its task. Re-submitting an id keeps the accumulated hit count, clears the
     * stale vector, revives an invalidated document, and supersedes any task still in flight.
     *
     * @return the id of the queued vectorisation task
     */
    @Transactional
    public String submit(String documentId, String content, @Nullable String channel) {
        Instant now = Instant.now();
        String resolvedChannel = StringUtils.defaultIfBlank(channel, DEFAULT_CHANNEL);

        Document document = documentRepository.findById(documentId)
                .map(existing -> {
                    existing.resubmit(content, resolvedChannel, now);
                    return existing;
                })
                .orElseGet(() -> new Document(documentId, content, resolvedChannel, now));
        documentRepository.save(document);

        return vectorizationService.enqueue(documentId, now);
    }

    /** Marks a document invalid so retrieval skips it. Idempotent. */
    @Transactional
    public Document invalidate(String documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        document.invalidate();
        return document;
    }

    @Transactional(readOnly = true)
    public Document get(String documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
    }

    @Transactional(readOnly = true)
    public Page<Document> list(@Nullable String channel, @Nullable DocumentStatus status, Pageable pageable) {
        if (channel != null && status != null) {
            return documentRepository.findByChannelAndStatus(channel, status, pageable);
        }
        if (channel != null) {
            return documentRepository.findByChannel(channel, pageable);
        }
        if (status != null) {
            return documentRepository.findByStatus(status, pageable);
        }
        return documentRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Optional<Document> find(String documentId) {
        return documentRepository.findById(documentId);
    }
}
