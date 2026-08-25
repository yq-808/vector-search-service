package com.example.vectorsearch.api;

import com.example.vectorsearch.api.dto.ErrorResponse;
import com.example.vectorsearch.api.dto.SearchResponse;
import com.example.vectorsearch.api.dto.SearchResultItem;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Retrieval behaviour, driven entirely through the HTTP API. */
class SearchApiIntegrationTest extends ApiIntegrationTest {

    @Test
    void ranksTheClosestDocumentFirst() {
        ingest("rank-exact", "vector search over embeddings", "ranking");
        ingest("rank-related", "searching vectors and embeddings at scale", "ranking");
        ingest("rank-unrelated", "banana bread recipe with walnuts", "ranking");

        SearchResponse response = search("vector search over embeddings", 3, "ranking");

        assertThat(response.results()).isNotEmpty();
        assertThat(response.results().get(0).documentId()).isEqualTo("rank-exact");
        assertThat(scores(response)).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void limitsTheNumberOfResults() {
        ingest("topk-1", "kayaking down the river", "topk");
        ingest("topk-2", "kayaking across the lake", "topk");
        ingest("topk-3", "kayaking in the sea", "topk");

        assertThat(search("kayaking", 2, "topk").results()).hasSize(2);
    }

    @Test
    void restrictsResultsToTheRequestedChannel() {
        ingest("channel-a", "shared subject matter", "channel-a");
        ingest("channel-b", "shared subject matter", "channel-b");

        SearchResponse response = search("shared subject matter", 10, "channel-a");

        assertThat(response.results()).extracting(SearchResultItem::documentId).containsExactly("channel-a");
        assertThat(response.results()).extracting(SearchResultItem::channel).containsExactly("channel-a");
    }

    @Test
    void stopsReturningADocumentOnceItIsInvalidated() {
        ingest("invalid-kept", "arctic expedition notes", "invalidation");
        ingest("invalid-retired", "arctic expedition notes", "invalidation");

        rest.postForObject("/api/v1/documents/{id}/invalidate", null, String.class, "invalid-retired");

        assertThat(search("arctic expedition notes", 10, "invalidation").results())
                .extracting(SearchResultItem::documentId)
                .containsExactly("invalid-kept");
    }

    @Test
    void countsEveryTimeADocumentIsRetrieved() {
        ingest("hits-counted", "how often was this retrieved", "hit-count");

        SearchResponse first = search("how often was this retrieved", 1, "hit-count");
        search("how often was this retrieved", 1, "hit-count");

        assertThat(first.results().get(0).hitCount()).isEqualTo(1);
        assertThat(document("hits-counted").hitCount()).isEqualTo(2);
    }

    @Test
    void returnsNothingForABlankQuery() {
        ingest("blank-query", "there is content here", "blank");

        assertThat(search("   ", 10, "blank").results()).isEmpty();
    }

    @Test
    void neverMatchesAnEmptyDocument() {
        ingest("empty-doc", "", "empty");

        assertThat(search("anything at all", 10, "empty").results()).isEmpty();
    }

    @Test
    void rejectsATopKBeyondTheConfiguredMaximum() {
        ResponseEntity<ErrorResponse> response = rest.postForEntity(
                "/api/v1/search", searchRequest("anything", 5000, null), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("topK must be between 1 and 100");
    }

    private static List<Double> scores(SearchResponse response) {
        return response.results().stream().map(SearchResultItem::score).toList();
    }
}
