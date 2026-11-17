package com.priorauth.workflow;

import java.time.Instant;
import java.util.Objects;

/** Immutable record of an authorization or appeal decision. */
public record Decision(
        String requestId,
        Reviewer reviewer,
        AuthStatus outcome,
        String reason,
        Instant decidedAt) {

    public Decision {
        Objects.requireNonNull(requestId, "Request ID is required");
        Objects.requireNonNull(reviewer, "Reviewer is required");
        Objects.requireNonNull(outcome, "Decision outcome is required");
        Objects.requireNonNull(reason, "Decision reason is required");
        Objects.requireNonNull(decidedAt, "Decision time is required");
        if (requestId.isBlank() || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Request ID and decision reason must not be blank");
        }
        if (outcome != AuthStatus.APPROVED && outcome != AuthStatus.DENIED
                && outcome != AuthStatus.OVERTURNED && outcome != AuthStatus.UPHELD) {
            throw new IllegalArgumentException("Unsupported decision outcome: " + outcome);
        }
        requestId = requestId.trim();
        reason = reason.trim();
    }
}
