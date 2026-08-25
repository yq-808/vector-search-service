package com.example.vectorsearch.api;

import com.example.vectorsearch.api.dto.SearchResultItem;
import com.example.vectorsearch.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The asynchronous contract, verified with a slow embedding step so the in-flight window is wide
 * enough to observe.
 */
@TestPropertySource(properties = {
        "vector.vectorization.simulated-cost-millis=1500",
        "spring.datasource.url=jdbc:h2:mem:vector-search-async;DB_CLOSE_DELAY=-1"
})
class AsyncVisibilityIntegrationTest extends ApiIntegrationTest {

    @Test
    void keepsADocumentOutOfRetrievalUntilItsVectorIsReady() {
        String taskId = submit("async-pending", "slowly vectorised content", "async").taskId();

        assertThat(task(taskId).status()).isIn(TaskStatus.QUEUED, TaskStatus.RUNNING);
        assertThat(document("async-pending").vectorReady()).isFalse();
        assertThat(search("slowly vectorised content", 10, "async").results()).isEmpty();

        awaitCompletion(taskId);

        assertThat(search("slowly vectorised content", 10, "async").results())
                .extracting(SearchResultItem::documentId)
                .containsExactly("async-pending");
    }

    @Test
    void discardsAnInFlightTaskWhenTheDocumentIsResubmitted() {
        String superseded = submit("async-superseded", "the first version", "async-resubmit").taskId();
        String current = submit("async-superseded", "the second version", "async-resubmit").taskId();

        awaitCompletion(current);

        assertThat(task(superseded).status()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(document("async-superseded").content()).isEqualTo("the second version");
        assertThat(document("async-superseded").vectorReady()).isTrue();
    }
}
