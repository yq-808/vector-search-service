package com.example.vectorsearch.vectorization;

import com.example.vectorsearch.config.VectorProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class VectorizationQueueTest {

    private final VectorizationQueue queue = queueOfCapacity(2);

    @Test
    void handsTasksBackInTheOrderTheyArrived() throws InterruptedException {
        queue.enqueue("first");
        queue.enqueue("second");

        assertThat(queue.take()).isEqualTo("first");
        assertThat(queue.take()).isEqualTo("second");
    }

    @Test
    void reportsItsDepth() {
        assertThat(queue.depth()).isZero();

        queue.enqueue("only");

        assertThat(queue.depth()).isEqualTo(1);
    }

    @Test
    void refusesWorkBeyondItsCapacity() {
        queue.enqueue("first");
        queue.enqueue("second");

        assertThatExceptionOfType(QueueOverflowException.class)
                .isThrownBy(() -> queue.enqueue("third"))
                .withMessageContaining("capacity 2");
    }

    @Test
    void failsFastWhenAskedForCapacityItDoesNotHave() {
        queue.enqueue("first");
        queue.requireCapacity();

        queue.enqueue("second");

        assertThatExceptionOfType(QueueOverflowException.class).isThrownBy(queue::requireCapacity);
    }

    @Test
    void blocksUntilATaskArrives() throws InterruptedException {
        Thread producer = new Thread(() -> queue.enqueue("late"));
        producer.start();

        assertThat(queue.take()).isEqualTo("late");
        producer.join();
    }

    private static VectorizationQueue queueOfCapacity(int capacity) {
        return new VectorizationQueue(new VectorProperties(
                new VectorProperties.Embedding(256),
                new VectorProperties.Vectorization(1, capacity, 0, 1000),
                new VectorProperties.Search(10, 100, 0.0)));
    }
}
