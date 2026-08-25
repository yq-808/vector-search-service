package com.example.vectorsearch.api.dto;

import com.example.vectorsearch.search.SearchHit;

import java.util.List;

public record SearchResponse(String query, String channel, int returned, List<SearchResultItem> results) {

    public static SearchResponse of(String query, String channel, List<SearchHit> hits) {
        List<SearchResultItem> items = hits.stream().map(SearchResultItem::from).toList();
        return new SearchResponse(query, channel, items.size(), items);
    }
}
