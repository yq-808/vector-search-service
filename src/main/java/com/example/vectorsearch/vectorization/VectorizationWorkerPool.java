package com.example.vectorsearch.vectorization;

import com.example.vectorsearch.config.VectorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The consumer side of the queue: a fixed set of threads, each looping "take a task id, run it".
 *
 * <p>Started and stopped by the Spring lifecycle, so workers only run while the application context
 * is up. On shutdown the threads are interrupted; whichever task was in flight is put back in the
 * queue by {@link VectorizationService}, so stopping the service never loses a submission.
 */
@Component
public class VectorizationWorkerPool implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(VectorizationWorkerPool.class);

    private final VectorizationQueue queue;
    private final VectorizationService vectorizationService;
    private final int workerCount;
    private final long shutdownTimeoutMillis;

    private final List<Thread> workers = new ArrayList<>();
    private volatile boolean running;

    public VectorizationWorkerPool(VectorizationQueue queue,
                                   VectorizationService vectorizationService,
                                   VectorProperties properties) {
        this.queue = queue;
        this.vectorizationService = vectorizationService;
        this.workerCount = properties.vectorization().workers();
        this.shutdownTimeoutMillis = properties.vectorization().shutdownTimeoutMillis();
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        for (int i = 0; i < workerCount; i++) {
            Thread worker = new Thread(this::consume, "vectorizer-" + i);
            worker.setDaemon(true);
            worker.start();
            workers.add(worker);
        }
        log.info("started {} vectorization worker(s)", workerCount);
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        workers.forEach(Thread::interrupt);
        for (Thread worker : workers) {
            try {
                worker.join(shutdownTimeoutMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        workers.clear();
        log.info("vectorization workers stopped, {} task(s) still queued", queue.depth());
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Below the web server's phase, which makes the workers start first and stop last. Shutdown
     * therefore drains HTTP before the workers go away, so a request still being served can still
     * hand its task to a running worker.
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 2048;
    }

    private void consume() {
        while (running) {
            String taskId;
            try {
                taskId = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                vectorizationService.process(taskId);
            } catch (RuntimeException e) {
                // A worker thread must outlive any single bad task.
                log.error("worker aborted on task {}", taskId, e);
            }
        }
    }
}
