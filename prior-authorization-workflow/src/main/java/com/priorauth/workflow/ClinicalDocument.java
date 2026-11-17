package com.priorauth.workflow;

import java.time.Instant;
import java.util.Objects;

/** A clinical document attached to an authorization request. */
public record ClinicalDocument(
        String documentType,
        String reference,
        Instant receivedAt) {

    public ClinicalDocument {
        Objects.requireNonNull(documentType, "Document type is required");
        if (documentType.isBlank()) {
            throw new IllegalArgumentException("Document type must not be blank");
        }
        documentType = documentType.trim();
        if (reference != null && reference.isBlank()) {
            reference = null;
        }
    }
}
