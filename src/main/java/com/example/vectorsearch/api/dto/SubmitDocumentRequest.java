package com.example.vectorsearch.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param documentId caller-chosen identifier; restricted to characters that survive a URL path,
 *                   since every document is addressed as {@code /documents/{documentId}}
 * @param content    empty content is accepted and maps to the zero vector, as specified
 * @param channel    provenance tag; defaults to {@code default} when omitted
 */
public record SubmitDocumentRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._~-]+",
                message = "must contain only letters, digits, '.', '_', '~' or '-'") String documentId,
        @NotNull @Size(max = 1_000_000) String content,
        @Size(max = 64) String channel) {
}
