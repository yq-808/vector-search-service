package com.example.vectorsearch.document;

/**
 * The two columns a similarity scan needs. Loading this instead of whole documents keeps the
 * CLOB content out of the scan.
 */
public record DocumentVector(String documentId, byte[] embedding) {
}
