package com.example.vectorsearch.api.dto;

import com.example.vectorsearch.search.SearchHit;

import java.time.Instant;

/**
 * @param hitCount the document's retrieval count, including this hit
 */
public record SearchResultItem(String documentId,
                               String channel,
                               double score,
                               String content,
                               long hitCount,
                               Instant submittedAt,
                               Instant vectorizedAt) {

    public static SearchResultItem from(SearchHit hit) {
        return new SearchResultItem(
                hit.document().getId(),
                hit.document().getChannel(),
                hit.score(),
                hit.document().getContent(),
                hit.document().getHitCount(),
                hit.document().getSubmittedAt(),
                hit.document().getVectorizedAt());
    }
}
