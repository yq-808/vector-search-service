package com.example.vectorsearch.search;

/** A document id and its similarity to the query, before the document itself is loaded. */
record ScoredDocument(String documentId, double score) {
}
