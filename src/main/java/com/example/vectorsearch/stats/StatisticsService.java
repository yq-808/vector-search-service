package com.example.vectorsearch.stats;

import com.example.vectorsearch.document.DocumentRepository;
import com.example.vectorsearch.document.DocumentStatus;
import com.example.vectorsearch.task.TaskStatus;
import com.example.vectorsearch.task.TaskStatusCount;
import com.example.vectorsearch.vectorization.VectorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

/** Aggregates the traceability counters kept on documents and tasks. */
@Service
public class StatisticsService {

    private final DocumentRepository documentRepository;
    private final VectorizationService vectorizationService;

    public StatisticsService(DocumentRepository documentRepository,
                             VectorizationService vectorizationService) {
        this.documentRepository = documentRepository;
        this.vectorizationService = vectorizationService;
    }

    @Transactional(readOnly = true)
    public Statistics snapshot() {
        Map<TaskStatus, Long> tasksByStatus = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            tasksByStatus.put(status, 0L);
        }
        for (TaskStatusCount count : vectorizationService.taskCounts()) {
            tasksByStatus.put(count.status(), count.count());
        }
        return new Statistics(
                documentRepository.count(),
                documentRepository.countByVectorReadyTrue(),
                documentRepository.countByStatus(DocumentStatus.INVALID),
                documentRepository.totalHits(),
                vectorizationService.queueDepth(),
                tasksByStatus,
                documentRepository.statsByChannel());
    }
}
