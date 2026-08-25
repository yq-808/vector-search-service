package com.example.vectorsearch.api;

import com.example.vectorsearch.task.TaskStatus;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

/** Many clients at once: nothing is lost, nothing is counted twice. */
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:vector-search-concurrency;DB_CLOSE_DELAY=-1")
class ConcurrencyIntegrationTest extends ApiIntegrationTest {

    private static final int THREADS = 8;

    @Test
    void vectorisesEverySimultaneousSubmission() {
        int documents = 24;

        List<String> taskIds = inParallel(documents,
                i -> submit("concurrent-" + i, "document number " + i + " about kayaking", "concurrent").taskId());

        assertThat(taskIds).doesNotContainNull().hasSize(documents);
        taskIds.forEach(taskId -> assertThat(awaitCompletion(taskId).status()).isEqualTo(TaskStatus.SUCCEEDED));

        JsonNode page = rest.getForObject("/api/v1/documents?channel=concurrent&size=100", JsonNode.class);
        assertThat(page.get("totalElements").asLong()).isEqualTo(documents);
    }

    @Test
    void countsEverySimultaneousHitExactlyOnce() {
        int searches = 32;
        ingest("concurrent-hits", "counting retrievals under load", "concurrent-hits");

        inParallel(searches, i -> search("counting retrievals under load", 1, "concurrent-hits").returned());

        assertThat(document("concurrent-hits").hitCount()).isEqualTo(searches);
    }

    private <T> List<T> inParallel(int count, IntFunction<T> action) {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    startGate.await();
                    return action.apply(index);
                }));
            }
            startGate.countDown();
            List<T> results = new ArrayList<>(count);
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } catch (Exception e) {
            throw new IllegalStateException("parallel execution failed", e);
        } finally {
            pool.shutdownNow();
        }
    }
}
