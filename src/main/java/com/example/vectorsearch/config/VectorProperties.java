package com.example.vectorsearch.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * All tunables of the service, bound from the {@code vector.*} configuration namespace.
 */
@Validated
@ConfigurationProperties(prefix = "vector")
public record VectorProperties(
        @NotNull @DefaultValue Embedding embedding,
        @NotNull @DefaultValue Vectorization vectorization,
        @NotNull @DefaultValue Search search) {

    /**
     * @param dimension length of every embedding vector; fixed at 256 by the service contract
     */
    public record Embedding(@DefaultValue("256") @Min(1) int dimension) {
    }

    /**
     * @param workers          number of threads draining the in-process vectorization queue
     * @param queueCapacity    maximum number of queued tasks before submissions are rejected
     * @param simulatedCostMillis artificial per-document latency that stands in for a remote embedding API
     * @param shutdownTimeoutMillis how long shutdown waits for in-flight tasks to stop
     */
    public record Vectorization(
            @DefaultValue("4") @Min(1) int workers,
            @DefaultValue("10000") @Min(1) int queueCapacity,
            @DefaultValue("200") @Min(0) long simulatedCostMillis,
            @DefaultValue("5000") @Min(0) long shutdownTimeoutMillis) {
    }

    /**
     * @param defaultTopK used when a search request omits {@code topK}
     * @param maxTopK     upper bound accepted for {@code topK}
     * @param minScore    results at or below this similarity are treated as noise and dropped
     */
    public record Search(
            @DefaultValue("10") @Min(1) int defaultTopK,
            @DefaultValue("100") @Min(1) int maxTopK,
            @DefaultValue("0.0") double minScore) {
    }
}
