package com.example.vectorsearch.embedding;

/**
 * Turns text into a fixed-length vector.
 *
 * <p>Implementations must be deterministic (the same text always yields the same vector),
 * thread-safe, and must map blank text to the zero vector.
 */
public interface EmbeddingModel {

    /** Length of every vector produced by this model. */
    int dimension();

    float[] embed(String text);
}
