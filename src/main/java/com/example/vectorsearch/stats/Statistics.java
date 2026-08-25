package com.example.vectorsearch.stats;

import com.example.vectorsearch.document.ChannelStats;
import com.example.vectorsearch.task.TaskStatus;

import java.util.List;
import java.util.Map;

/** A snapshot of what the service currently holds and how busy it is. */
public record Statistics(long totalDocuments,
                         long vectorReadyDocuments,
                         long invalidDocuments,
                         long totalSearchHits,
                         int queueDepth,
                         Map<TaskStatus, Long> tasksByStatus,
                         List<ChannelStats> channels) {
}
