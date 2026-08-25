package com.example.vectorsearch.vectorization;

import com.example.vectorsearch.config.VectorProperties;
import com.example.vectorsearch.document.Document;
import com.example.vectorsearch.document.DocumentRepository;
import com.example.vectorsearch.embedding.HashingEmbeddingModel;
import com.example.vectorsearch.task.TaskStatus;
import com.example.vectorsearch.task.VectorizationTask;
import com.example.vectorsearch.task.VectorizationTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit coverage for the worker-side state machine, with the database and clock mocked out. */
@ExtendWith(MockitoExtension.class)
class VectorizationServiceTest {

    private static final String TASK_ID = "task-1";
    private static final String DOCUMENT_ID = "doc-1";

    @Mock
    private VectorizationTaskRepository taskRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private VectorizationQueue queue;
    private VectorizationService service;

    @BeforeEach
    void setUp() {
        queue = new VectorizationQueue(properties(10));
        service = new VectorizationService(taskRepository, documentRepository,
                new HashingEmbeddingModel(properties(10)), queue, transactionManager, properties(10));
    }

    @Test
    void doesNothingWhenAnotherWorkerAlreadyClaimedTheTask() {
        when(taskRepository.claim(eq(TASK_ID), any())).thenReturn(false);

        service.process(TASK_ID);

        verifyNoInteractions(documentRepository);
        verify(taskRepository, never()).finish(anyString(), any(), any(), any());
    }

    @Test
    void marksTheTaskFailedWhenItsDocumentIsGone() {
        claimable();
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.empty());

        service.process(TASK_ID);

        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(taskRepository).finish(eq(TASK_ID), eq(TaskStatus.FAILED), error.capture(), any());
        assertThat(error.getValue()).contains("document not found");
    }

    @Test
    void attachesTheVectorToTheDocumentOnSuccess() {
        claimable();
        Document document = document();
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));
        inTransaction();
        when(taskRepository.finish(eq(TASK_ID), eq(TaskStatus.SUCCEEDED), any(), any())).thenReturn(true);

        service.process(TASK_ID);

        assertThat(document.isVectorReady()).isTrue();
        assertThat(document.getVectorizedAt()).isNotNull();
        assertThat(document.getEmbedding()).hasSize(256 * Float.BYTES);
    }

    @Test
    void discardsTheVectorWhenTheDocumentWasResubmittedMidFlight() {
        claimable();
        Document document = document();
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));
        inTransaction();
        when(taskRepository.finish(eq(TASK_ID), eq(TaskStatus.SUCCEEDED), any(), any())).thenReturn(false);

        service.process(TASK_ID);

        assertThat(document.isVectorReady()).isFalse();
        assertThat(document.getEmbedding()).isNull();
    }

    @Test
    void supersedesEarlierTasksWhenQueueingANewOne() {
        Instant now = Instant.now();
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        String taskId = service.enqueue(DOCUMENT_ID, now);

        verify(taskRepository).cancelInFlight(DOCUMENT_ID, now);
        assertThat(taskId).isNotBlank();
        assertThat(queue.depth()).isEqualTo(1);
    }

    @Test
    void refusesToQueueWorkOnceTheBacklogIsFull() {
        VectorizationQueue full = new VectorizationQueue(properties(1));
        full.enqueue("already-there");
        VectorizationService constrained = new VectorizationService(taskRepository, documentRepository,
                new HashingEmbeddingModel(properties(1)), full, transactionManager, properties(1));

        assertThatExceptionOfType(QueueOverflowException.class)
                .isThrownBy(() -> constrained.enqueue(DOCUMENT_ID, Instant.now()));

        verify(taskRepository, never()).save(any());
    }

    private void claimable() {
        when(taskRepository.claim(eq(TASK_ID), any())).thenReturn(true);
        when(taskRepository.findById(TASK_ID))
                .thenReturn(Optional.of(new VectorizationTask(TASK_ID, DOCUMENT_ID, Instant.now())));
    }

    private void inTransaction() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private static Document document() {
        return new Document(DOCUMENT_ID, "some content to vectorise", "default", Instant.now());
    }

    private static VectorProperties properties(int queueCapacity) {
        return new VectorProperties(
                new VectorProperties.Embedding(256),
                new VectorProperties.Vectorization(1, queueCapacity, 0, 1000),
                new VectorProperties.Search(10, 100, 0.0));
    }
}
