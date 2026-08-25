package com.example.vectorsearch.document;

public enum DocumentStatus {

    /** Participates in retrieval once its vector is ready. */
    ACTIVE,

    /** Explicitly retired by an operator; never returned by a search. */
    INVALID
}
