package com.example.vectorsearch.api;

import com.example.vectorsearch.api.dto.DocumentResponse;
import com.example.vectorsearch.api.dto.PageResponse;
import com.example.vectorsearch.api.dto.SubmitDocumentRequest;
import com.example.vectorsearch.api.dto.SubmitDocumentResponse;
import com.example.vectorsearch.api.dto.TaskResponse;
import com.example.vectorsearch.document.Document;
import com.example.vectorsearch.document.DocumentService;
import com.example.vectorsearch.document.DocumentStatus;
import com.example.vectorsearch.task.VectorizationTask;
import com.example.vectorsearch.vectorization.VectorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents")
@Validated
public class DocumentController {

    private final DocumentService documentService;
    private final VectorizationService vectorizationService;

    public DocumentController(DocumentService documentService, VectorizationService vectorizationService) {
        this.documentService = documentService;
        this.vectorizationService = vectorizationService;
    }

    /** Accepts a document and queues it. 202, because the vector does not exist yet. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmitDocumentResponse submit(@Valid @RequestBody SubmitDocumentRequest request) {
        String taskId = documentService.submit(request.documentId(), request.content(), request.channel());
        return SubmitDocumentResponse.accepted(taskId, request.documentId());
    }

    @GetMapping
    public PageResponse<DocumentResponse> list(
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        Page<Document> documents = documentService.list(channel, status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt")));
        return PageResponse.of(documents, DocumentResponse::from);
    }

    @GetMapping("/{documentId}")
    public DocumentResponse get(@PathVariable String documentId) {
        return DocumentResponse.from(documentService.get(documentId));
    }

    /** Retires a document: it stops being a retrieval candidate but keeps its history. */
    @PostMapping("/{documentId}/invalidate")
    public DocumentResponse invalidate(@PathVariable String documentId) {
        return DocumentResponse.from(documentService.invalidate(documentId));
    }

    /** The most recent vectorisation task of this document. */
    @GetMapping("/{documentId}/task")
    public TaskResponse latestTask(@PathVariable String documentId) {
        VectorizationTask task = vectorizationService.getLatestTaskOfDocument(documentId);
        return TaskResponse.from(task, documentService.find(documentId).orElse(null));
    }
}
