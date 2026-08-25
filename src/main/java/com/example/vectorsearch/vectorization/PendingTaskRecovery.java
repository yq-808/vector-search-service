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
        pending.forEach(queue::enqueue);
        if (!pending.isEmpty()) {
            log.info("recovered {} pending task(s), {} of which were interrupted mid-run",
                    pending.size(), orphaned);
        }
    }
}
