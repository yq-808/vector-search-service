package com.example.vectorsearch.vectorization;

import com.example.vectorsearch.config.VectorProperties;
import com.example.vectorsearch.document.Document;
import com.example.vectorsearch.document.DocumentNotFoundException;
import com.example.vectorsearch.document.DocumentRepository;
import com.example.vectorsearch.embedding.EmbeddingModel;
import com.example.vectorsearch.embedding.Vectors;
import com.example.vectorsearch.task.TaskNotFoundException;
import com.example.vectorsearch.task.TaskStatus;
import com.example.vectorsearch.task.TaskStatusCount;
import com.example.vectorsearch.task.VectorizationTask;
import com.example.vectorsearch.task.VectorizationTaskRepository;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Owns the asynchronous half of ingestion: creating tasks, and running them on worker threads.
 *
 * <p>Vectorisation is deliberately <em>not</em> wrapped in one long transaction. The simulated
 * embedding call happens outside any transaction, framed by two short ones (claim, then store), so
 * a slow document never pins a database connection.
 */
@Service
public class VectorizationService {

    private static final Logger log = LoggerFactory.getLogger(VectorizationService.class);
    private static final int MAX_ERROR_LENGTH = 1000;

    private final VectorizationTaskRepository taskRepository;
    private final DocumentRepository documentRepository;
    private final EmbeddingModel embeddingModel;
    private final VectorizationQueue queue;
    private final TransactionTemplate transactionTemplate;
    private final long simulatedCostMillis;

    public VectorizationService(VectorizationTaskRepository taskRepository,
                                DocumentRepository documentRepository,
                                EmbeddingModel embeddingModel,
                                VectorizationQueue queue,
                                PlatformTransactionManager transactionManager,
                                VectorProperties properties) {
        this.taskRepository = taskRepository;
        this.documentRepository = documentRepository;
        this.embeddingModel = embeddingModel;
        this.queue = queue;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.simulatedCostMillis = properties.vectorization().simulatedCostMillis();
    }

    /**
     * Records a task for the document and hands it to the workers.
     *
     * <p>The hand-off happens <em>after</em> the surrounding transaction commits. Enqueueing any
     * earlier would let a worker claim a task row that is not visible to it yet.
     *
     * @return the task id
     */
    @Transactional
    public String enqueue(String documentId, Instant now) {
        queue.requireCapacity();
        taskRepository.cancelInFlight(documentId, now);
        VectorizationTask task = taskRepository.save(
                new VectorizationTask(UUID.randomUUID().toString(), documentId, now));
        publishAfterCommit(task.getId());
        return task.getId();
    }

    /** Runs one task. Called on a worker thread; safe to call concurrently with the same id. */
    public void process(String taskId) {
        if (!taskRepository.claim(taskId, Instant.now())) {
            log.debug("task {} is no longer queued, skipping", taskId);
            return;
        }
        VectorizationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> TaskNotFoundException.forTask(taskId));
        try {
            store(task, vectorize(contentOf(task.getDocumentId())));
        } catch (InterruptedException e) {
            taskRepository.requeue(taskId);
            Thread.currentThread().interrupt();
            log.info("task {} interrupted, returned to the queue", taskId);
        } catch (RuntimeException e) {
            log.warn("vectorisation failed for task {}", taskId, e);
            taskRepository.finish(taskId, TaskStatus.FAILED,
                    StringUtils.abbreviate(e.toString(), MAX_ERROR_LENGTH), Instant.now());
        }
    }

    @Transactional(readOnly = true)
    public VectorizationTask getTask(String taskId) {
        return taskRepository.findById(taskId).orElseThrow(() -> TaskNotFoundException.forTask(taskId));
    }

    @Transactional(readOnly = true)
    public VectorizationTask getLatestTaskOfDocument(String documentId) {
        return taskRepository.findFirstByDocumentIdOrderByCreatedAtDescIdDesc(documentId)
                .orElseThrow(() -> TaskNotFoundException.forDocument(documentId));
    }

    @Transactional(readOnly = true)
    public List<TaskStatusCount> taskCounts() {
        return taskRepository.countByStatus();
    }

    public int queueDepth() {
        return queue.depth();
    }

    private String contentOf(String documentId) {
        return documentRepository.findById(documentId)
                .map(Document::getContent)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
    }

    /** The expensive part. The sleep stands in for the latency of a real embedding API. */
    private float[] vectorize(String content) throws InterruptedException {
        if (simulatedCostMillis > 0) {
            Thread.sleep(simulatedCostMillis);
        }
        return embeddingModel.embed(content);
    }

    /**
     * Marks the task done and attaches the vector to the document, atomically. If the task is no
     * longer {@code RUNNING} the document has been re-submitted in the meantime and this vector
     * describes content that is already gone, so it is dropped.
     */
    private void store(VectorizationTask task, float[] vector) {
        Instant finishedAt = Instant.now();
        Boolean applied = transactionTemplate.execute(status -> {
            if (!taskRepository.finish(task.getId(), TaskStatus.SUCCEEDED, null, finishedAt)) {
                return false;
            }
            Document document = documentRepository.findById(task.getDocumentId())
                    .orElseThrow(() -> new DocumentNotFoundException(task.getDocumentId()));
            document.applyVector(Vectors.toBytes(vector), finishedAt);
            return true;
        });
        if (!Boolean.TRUE.equals(applied)) {
            log.info("task {} was superseded while running, discarding its vector", task.getId());
        }
    }

    private void publishAfterCommit(String taskId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            queue.enqueue(taskId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    queue.enqueue(taskId);
                } catch (QueueOverflowException e) {
                    // The task row is committed and QUEUED; recovery will pick it up on restart.
                    log.error("queue filled up before task {} could be published", taskId, e);
                }
            }
        });
    }
}
