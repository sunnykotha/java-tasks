package com.priorauth.workflow;

import java.util.Objects;

/** Identifies the person and role responsible for a review action. */
public record Reviewer(String reviewerId, ReviewerRole role) {
    public Reviewer {
        Objects.requireNonNull(reviewerId, "Reviewer ID is required");
        Objects.requireNonNull(role, "Reviewer role is required");
        if (reviewerId.isBlank()) {
            throw new IllegalArgumentException("Reviewer ID must not be blank");
        }
        reviewerId = reviewerId.trim();
    }
}
