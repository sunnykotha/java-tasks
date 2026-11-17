package com.priorauth.workflow;

import java.time.Instant;
import java.util.Objects;

/** Immutable audit-friendly description of a lifecycle state change. */
public record StateTransition(
        String requestId,
        AuthStatus fromStatus,
        AuthStatus toStatus,
        String actorId,
        Instant timestamp) {

    public StateTransition {
        Objects.requireNonNull(requestId, "Request ID is required");
        Objects.requireNonNull(toStatus, "Destination status is required");
        Objects.requireNonNull(actorId, "Actor ID is required");
        Objects.requireNonNull(timestamp, "Transition timestamp is required");
        if (requestId.isBlank() || actorId.isBlank()) {
            throw new IllegalArgumentException("Request ID and actor ID must not be blank");
        }
        requestId = requestId.trim();
        actorId = actorId.trim();
    }
}
