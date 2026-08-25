package com.example.vectorsearch.vectorization;

/** Thrown when the vectorisation backlog is at capacity and the caller should retry later. */
public class QueueOverflowException extends RuntimeException {

    public QueueOverflowException(int capacity) {
        super("vectorization queue is full (capacity " + capacity + "), retry later");
    }
}
