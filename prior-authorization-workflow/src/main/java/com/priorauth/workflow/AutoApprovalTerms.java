package com.priorauth.workflow;

import java.time.Instant;
import java.util.Objects;

/** Approval validity and unit allowance configured for an auto-approved request. */
public record AutoApprovalTerms(
        Instant effectiveAt,
        Instant expiresAt,
        int approvedUnits) {

    public AutoApprovalTerms {
        Objects.requireNonNull(effectiveAt, "Effective time is required");
        Objects.requireNonNull(expiresAt, "Expiry time is required");
        if (!expiresAt.isAfter(effectiveAt)) {
            throw new IllegalArgumentException(
                    "Expiry time must be after effective time");
        }
        if (approvedUnits <= 0) {
            throw new IllegalArgumentException(
                    "Approved units must be positive");
        }
    }
}
