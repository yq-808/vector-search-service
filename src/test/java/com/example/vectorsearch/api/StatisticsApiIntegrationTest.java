package com.example.vectorsearch.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatisticsApiIntegrationTest extends ApiIntegrationTest {

    @Test
    void reportsCorpusAndRetrievalCounters() {
        ingest("doc-stats-1", "statistics about penguins", "stats");
        ingest("doc-stats-2", "statistics about pelicans", "stats");
        search("penguins", 5, "stats");

        JsonNode stats = rest.getForObject("/api/v1/stats", JsonNode.class);

        assertThat(stats.get("totalDocuments").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(stats.get("vectorReadyDocuments").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(stats.get("totalSearchHits").asLong()).isPositive();
        assertThat(stats.get("tasksByStatus").get("SUCCEEDED").asLong()).isGreaterThanOrEqualTo(2);

        JsonNode statsChannel = channel(stats, "stats");
        assertThat(statsChannel.get("documents").asLong()).isEqualTo(2);
        assertThat(statsChannel.get("hits").asLong()).isPositive();
    }

    private static JsonNode channel(JsonNode stats, String name) {
        for (JsonNode channel : stats.get("channels")) {
            if (name.equals(channel.get("channel").asText())) {
                return channel;
            }
        }
        throw new AssertionError("no statistics for channel " + name);
    }
}
