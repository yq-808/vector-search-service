package com.example.vectorsearch.task;

/** Number of tasks currently in a given state. */
public record TaskStatusCount(TaskStatus status, long count) {
}
