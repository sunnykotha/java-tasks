package com.priorauth.workflow;

import java.time.Instant;
import java.util.Objects;

/** Dedicated immutable audit-entry domain entity. */
public record AuditEntry(
        String requestId,
        Instant timestamp,
        String actor,
        String eventType,
        String reason) {

    public AuditEntry {
        Objects.requireNonNull(requestId, "Request ID is required");
        Objects.requireNonNull(timestamp, "Timestamp is required");
        Objects.requireNonNull(actor, "Actor is required");
        Objects.requireNonNull(eventType, "Event type is required");
        Objects.requireNonNull(reason, "Reason is required");
        if (requestId.isBlank() || actor.isBlank()
                || eventType.isBlank() || reason.isBlank()) {
            throw new IllegalArgumentException("Audit fields must not be blank");
        }
    }
}
