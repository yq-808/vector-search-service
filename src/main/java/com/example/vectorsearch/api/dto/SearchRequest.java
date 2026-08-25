package com.example.vectorsearch.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param topK    number of results; falls back to the configured default when omitted
 * @param channel restricts results to one channel when set
 */
public record SearchRequest(@NotNull String query,
                            @Min(1) Integer topK,
                            @Size(max = 64) String channel) {
}
