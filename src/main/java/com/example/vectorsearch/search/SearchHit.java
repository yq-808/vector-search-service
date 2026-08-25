package com.example.vectorsearch.search;

import com.example.vectorsearch.document.Document;

/** A retrieved document together with its similarity score. */
public record SearchHit(Document document, double score) {
}
