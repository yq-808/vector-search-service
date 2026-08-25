package com.example.vectorsearch.api;

import com.example.vectorsearch.api.dto.SearchRequest;
import com.example.vectorsearch.api.dto.SearchResponse;
import com.example.vectorsearch.search.SearchHit;
import com.example.vectorsearch.search.SearchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /** Synchronous retrieval. Only documents whose vectorisation finished can match. */
    @PostMapping
    public SearchResponse search(@Valid @RequestBody SearchRequest request) {
        List<SearchHit> hits = searchService.search(request.query(), request.topK(), request.channel());
        return SearchResponse.of(request.query(), request.channel(), hits);
    }
}
