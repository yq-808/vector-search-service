package com.example.vectorsearch.vectorization;

import com.example.vectorsearch.task.TaskStatus;
import com.example.vectorsearch.task.VectorizationTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reconnects the durable task table to the in-memory queue at startup.
 *
 * <p>The queue itself does not survive a restart, but the task rows do. Tasks left {@code RUNNING}
 * by an unclean shutdown are returned to {@code QUEUED}, and everything queued is published again.
 *
 * <p>Publishing happens on its own thread and blocks for room rather than failing: a backlog left
 * by a crash can be larger than the queue is allowed to hold, and neither refusing to start nor
 * dropping the excess is an acceptable answer. Startup therefore does not wait for the backlog
 * either &mdash; the workers are already running and begin draining immediately.
 */
@Component
public class PendingTaskRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PendingTaskRecovery.class);

    private final VectorizationTaskRepository taskRepository;
    private final VectorizationQueue queue;

    public PendingTaskRecovery(VectorizationTaskRepository taskRepository, VectorizationQueue queue) {
        this.taskRepository = taskRepository;
        this.queue = queue;
    }

    @Override
    public void run(ApplicationArguments args) {
        int orphaned = taskRepository.resetOrphanedTasks();
        List<String> pending = taskRepository.findIdsByStatus(TaskStatus.QUEUED);
        if (pending.isEmpty()) {
            return;
        }
        log.info("recovering {} pending task(s), {} of which were interrupted mid-run",
                pending.size(), orphaned);
        Thread publisher = new Thread(() -> publish(pending), "task-recovery");
        publisher.setDaemon(true);
        publisher.start();
    }

    /**
     * Re-queues a snapshot of the pending tasks. An id that a worker has already moved on from is
     * harmless: its {@code claim} simply finds nothing to claim and the worker moves on too.
     */
    void publish(List<String> taskIds) {
        for (int i = 0; i < taskIds.size(); i++) {
            try {
                queue.put(taskIds.get(i));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("recovery interrupted after {} of {} task(s); the rest stay QUEUED "
                        + "and are picked up at the next startup", i, taskIds.size());
                return;
            }
        }
        log.info("re-queued {} pending task(s)", taskIds.size());
    }
}
