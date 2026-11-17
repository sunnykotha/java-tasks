package com.priorauth.workflow;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Coordinates a reviewer decision with creation and retrieval of approved
 * authorizations. In-memory storage is used for this project.
 */
public final class AuthorizationDecisionWorkflow {
    private final DecisionService decisionService;
    private final Map<String, ApprovedAuthorization> approvals = new HashMap<>();

    public AuthorizationDecisionWorkflow(DecisionService decisionService) {
        this.decisionService = Objects.requireNonNull(
                decisionService, "decisionService");
    }

    public synchronized AuthStatus decide(
            String requestId,
            String actor,
            ReviewerRole role,
            AuthStatus currentStatus,
            boolean approve,
            String reason,
            Instant decisionAt,
            Instant effectiveAt,
            Instant expiresAt,
            int approvedUnits) {

        Objects.requireNonNull(requestId, "requestId");

        if (approve) {
            if (approvals.containsKey(requestId)) {
                throw new IllegalStateException(
                        "An approved authorization already exists for request: "
                                + requestId);
            }

            // Validate approval details before recording an approval decision.
            new ApprovedAuthorization(
                    AuthStatus.APPROVED, effectiveAt, expiresAt, approvedUnits);
        }

        AuthStatus result = decisionService.decide(
                requestId, actor, role, currentStatus, approve, reason, decisionAt);

        if (result == AuthStatus.APPROVED) {
            approvals.put(requestId, new ApprovedAuthorization(
                    result, effectiveAt, expiresAt, approvedUnits));
        }

        return result;
    }

    public synchronized AuthStatus reviewAppeal(
            String requestId, String actor, ReviewerRole role,
            String originalReviewer, AuthStatus currentStatus,
            boolean overturn, String reason, Instant timestamp) {
        return decisionService.reviewAppeal(
                requestId, actor, role, originalReviewer,
                currentStatus, overturn, reason, timestamp);
    }

    public synchronized java.util.List<Decision> getDecisionsForRequest(
            String requestId) {
        return decisionService.getDecisionsForRequest(requestId);
    }

    public synchronized void registerAutomaticApproval(
            String requestId, AutoApprovalTerms terms) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("Request ID is required");
        }
        Objects.requireNonNull(terms, "Automatic approval terms are required");
        if (approvals.containsKey(requestId)) {
            throw new IllegalStateException(
                    "An approved authorization already exists for request: "
                            + requestId);
        }
        approvals.put(requestId, new ApprovedAuthorization(
                AuthStatus.APPROVED, terms.effectiveAt(),
                terms.expiresAt(), terms.approvedUnits()));
    }

    public synchronized Optional<ApprovedAuthorization> findApproval(
            String requestId) {
        Objects.requireNonNull(requestId, "requestId");
        return Optional.ofNullable(approvals.get(requestId));
    }

    public synchronized void consumeClaim(
            String requestId, String claimId, int units, Instant serviceAt) {
        Objects.requireNonNull(requestId, "requestId");

        ApprovedAuthorization authorization = approvals.get(requestId);
        if (authorization == null) {
            throw new IllegalStateException(
                    "No approved authorization exists for request: " + requestId);
        }

        authorization.consumeClaim(claimId, units, serviceAt);
    }
}