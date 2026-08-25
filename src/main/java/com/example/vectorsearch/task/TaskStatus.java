package com.example.vectorsearch.task;

import java.util.Set;

/** Lifecycle of a vectorisation task. */
public enum TaskStatus {

    /** Waiting in the in-process queue. */
    QUEUED,

    /** Claimed by a worker and currently being vectorised. */
    RUNNING,

    /** Vector computed and stored; the document is now searchable. */
    SUCCEEDED,

    /** Vectorisation failed; {@code errorMessage} says why. */
    FAILED,

    /** Superseded by a newer submission of the same document; its result is discarded. */
    CANCELLED;

    /** States from which a task can still reach {@link #SUCCEEDED}. */
    public static final Set<TaskStatus> IN_FLIGHT = Set.of(QUEUED, RUNNING);
}
