package com.example.vectorsearch.task;

public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(String message) {
        super(message);
    }

    public static TaskNotFoundException forTask(String taskId) {
        return new TaskNotFoundException("task not found: " + taskId);
    }

    public static TaskNotFoundException forDocument(String documentId) {
        return new TaskNotFoundException("no vectorization task for document: " + documentId);
    }
}
