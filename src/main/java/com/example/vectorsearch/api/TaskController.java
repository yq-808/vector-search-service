package com.example.vectorsearch.api;

import com.example.vectorsearch.api.dto.TaskResponse;
import com.example.vectorsearch.document.DocumentService;
import com.example.vectorsearch.task.VectorizationTask;
import com.example.vectorsearch.vectorization.VectorizationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final VectorizationService vectorizationService;
    private final DocumentService documentService;

    public TaskController(VectorizationService vectorizationService, DocumentService documentService) {
        this.vectorizationService = vectorizationService;
        this.documentService = documentService;
    }

    /** Polling endpoint: reports queue position implicitly through the task status. */
    @GetMapping("/{taskId}")
    public TaskResponse get(@PathVariable String taskId) {
        VectorizationTask task = vectorizationService.getTask(taskId);
        return TaskResponse.from(task, documentService.find(task.getDocumentId()).orElse(null));
    }
}
