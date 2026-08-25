package com.example.vectorsearch.vectorization;

import com.example.vectorsearch.config.VectorProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The in-process work queue: a bounded {@link LinkedBlockingQueue} of task ids, no broker involved.
 *
 * <p>Only ids travel through it. The task row in the database is the durable copy, so a task that
 * never reaches a worker &mdash; because the process stopped &mdash; is simply re-queued at the
 * next startup by {@link PendingTaskRecovery}.
 *
 * <p>The queue is bounded on purpose: a full queue is reported to the caller as backpressure
 * instead of being absorbed into unbounded heap usage.
 */
@Component
public class VectorizationQueue {

    private final BlockingQueue<String> pending;
    private final int capacity;

    public VectorizationQueue(VectorProperties properties) {
        this.capacity = properties.vectorization().queueCapacity();
        this.pending = new LinkedBlockingQueue<>(capacity);
    }

    /** @throws QueueOverflowException if the queue is full */
    public void enqueue(String taskId) {
        if (!pending.offer(taskId)) {
            throw new QueueOverflowException(capacity);
        }
    }

    /**
     * Fails fast before any work is persisted. The queue may still fill up between this check and
     * the actual enqueue, which is why {@link #enqueue} keeps its own guard.
     */
    public void requireCapacity() {
        if (pending.remainingCapacity() == 0) {
            throw new QueueOverflowException(capacity);
        }
    }

    /**
     * Enqueues a task id, waiting for room if the queue is full.
     *
     * <p>Used by startup recovery, where the durable backlog can legitimately be larger than the
     * queue and there is no caller to push back on &mdash; only workers to wait for.
     */
    public void put(String taskId) throws InterruptedException {
        pending.put(taskId);
    }

    /** Blocks until a task id is available. */
    public String take() throws InterruptedException {
        return pending.take();
    }

    public int depth() {
        return pending.size();
    }

    public int capacity() {
        return capacity;
    }
}
