package com.example.vectorsearch.vectorization;

import com.example.vectorsearch.config.VectorProperties;
import com.example.vectorsearch.task.TaskStatus;
import com.example.vectorsearch.task.VectorizationTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Startup recovery: everything still pending goes back on the queue, whatever its size. */
@ExtendWith(MockitoExtension.class)
class PendingTaskRecoveryTest {

    @Mock
    private VectorizationTaskRepository taskRepository;

    @Test
    void requeuesEveryPendingTaskAndRevivesOrphanedOnes() {
        VectorizationQueue queue = queueOfCapacity(10);
        when(taskRepository.findIdsByStatus(TaskStatus.QUEUED)).thenReturn(List.of("task-a", "task-b"));

        new PendingTaskRecovery(taskRepository, queue).run(null);

        awaitDepth(queue, 2);
        verify(taskRepository).resetOrphanedTasks();
    }

    @Test
    void doesNothingWhenNothingIsPending() {
        VectorizationQueue queue = queueOfCapacity(10);
        when(taskRepository.findIdsByStatus(TaskStatus.QUEUED)).thenReturn(List.of());

        new PendingTaskRecovery(taskRepository, queue).run(null);

        assertThat(queue.depth()).isZero();
    }

    /**
     * A crash can leave more pending tasks than the queue is allowed to hold. Recovery must wait
     * for the workers to make room rather than overflow, which used to abort startup outright.
     */
    @Test
    void publishesABacklogLargerThanTheQueueItself() throws InterruptedException {
        VectorizationQueue queue = queueOfCapacity(2);
        List<String> backlog = IntStream.rangeClosed(1, 20).mapToObj(i -> "task-" + i).toList();
        List<String> drained = new CopyOnWriteArrayList<>();

        Thread worker = new Thread(() -> {
            try {
                while (drained.size() < backlog.size()) {
                    drained.add(queue.take());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        worker.start();

        new PendingTaskRecovery(taskRepository, queue).publish(backlog);
        worker.join(Duration.ofSeconds(10).toMillis());

        assertThat(drained).containsExactlyElementsOf(backlog);
    }

    private static void awaitDepth(VectorizationQueue queue, int expected) {
        Instant deadline = Instant.now().plusSeconds(5);
        while (queue.depth() != expected && Instant.now().isBefore(deadline)) {
            Thread.onSpinWait();
        }
        assertThat(queue.depth()).isEqualTo(expected);
    }

    private static VectorizationQueue queueOfCapacity(int capacity) {
        return new VectorizationQueue(new VectorProperties(
                new VectorProperties.Embedding(256),
                new VectorProperties.Vectorization(1, capacity, 0, 1000),
                new VectorProperties.Search(10, 100, 0.0)));
    }
}
