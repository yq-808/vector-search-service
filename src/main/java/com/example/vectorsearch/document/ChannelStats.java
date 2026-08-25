package com.example.vectorsearch.document;

/** Per-channel rollup used by the statistics endpoint. */
public record ChannelStats(String channel, long documents, long hits) {
}
