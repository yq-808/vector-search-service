package com.example.vectorsearch.document;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String documentId) {
        super("document not found: " + documentId);
    }
}
