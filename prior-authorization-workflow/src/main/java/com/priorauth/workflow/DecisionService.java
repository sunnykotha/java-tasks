package com.priorauth.workflow;

import java.time.Instant;
import java.util.Objects;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DecisionService {

    private final AuditLog auditLog;
    private final AuthorizationPermissionService permissionService;
    private final Map<String, List<Decision>> decisions = new HashMap<>();

    public DecisionService(AuditLog auditLog) {
        this.auditLog = Objects.requireNonNull(
                auditLog, "Audit log is required");
        this.permissionService = new AuthorizationPermissionService();
    }

    public AuthStatus decide(
            String requestId,
            String actor,
            ReviewerRole role,
            AuthStatus currentStatus,
            boolean approve,
            String reason,
            Instant timestamp) {

        requireText(requestId, "Request ID");
        requireText(actor, "Actor");
        requireText(reason, "Reason");
        Objects.requireNonNull(role, "Role is required");
        Objects.requireNonNull(currentStatus, "Current status is required");
        Objects.requireNonNull(timestamp, "Timestamp is required");

        if (currentStatus != AuthStatus.UNDER_REVIEW) {
            throw new IllegalStateException(
                    "A decision requires UNDER_REVIEW status");
        }

        AuthStatus result = approve
                ? AuthStatus.APPROVED
                : AuthStatus.DENIED;

        permissionService.validatePermission(
                role, currentStatus, result, actor, null);

        String eventType = approve ? "APPROVED" : "DENIED";
        auditLog.record(
                requestId, timestamp, actor.trim(), eventType, reason);
        recordDecision(new Decision(requestId, new Reviewer(actor, role),
                result, reason, timestamp));

        return result;
    }

    public AuthStatus reviewAppeal(
            String requestId,
            String actor,
            ReviewerRole role,
            String originalReviewer,
            AuthStatus currentStatus,
            boolean overturn,
            String reason,
            Instant timestamp) {

        requireText(requestId, "Request ID");
        requireText(actor, "Actor");
        requireText(originalReviewer, "Original reviewer");
        requireText(reason, "Reason");
        Objects.requireNonNull(role, "Role is required");
        Objects.requireNonNull(currentStatus, "Current status is required");
        Objects.requireNonNull(timestamp, "Timestamp is required");

        if (currentStatus != AuthStatus.APPEALED
                && currentStatus != AuthStatus.APPEAL_REVIEW) {
            throw new IllegalStateException(
                    "Appeal review requires APPEALED or APPEAL_REVIEW status");
        }

        AuthStatus result = overturn
                ? AuthStatus.OVERTURNED
                : AuthStatus.UPHELD;

        AuthStatus permissionFrom = currentStatus == AuthStatus.APPEALED
                ? AuthStatus.APPEALED
                : AuthStatus.APPEAL_REVIEW;

        permissionService.validatePermission(
                role, permissionFrom,
                currentStatus == AuthStatus.APPEALED
                        ? AuthStatus.APPEAL_REVIEW : result,
                actor, originalReviewer);

        // Validate all permissions before writing any audit event.
        if (currentStatus == AuthStatus.APPEALED) {
            permissionService.validatePermission(
                    role, AuthStatus.APPEAL_REVIEW, result,
                    actor, originalReviewer);
        }

        if (currentStatus == AuthStatus.APPEALED) {
            auditLog.record(
                    requestId,
                    timestamp,
                    actor.trim(),
                    "APPEAL_REVIEW_STARTED",
                    "Independent appeal review started");
        }

        auditLog.record(
                requestId,
                timestamp,
                actor.trim(),
                overturn ? "APPEAL_OVERTURNED" : "APPEAL_UPHELD",
                reason);
        recordDecision(new Decision(requestId, new Reviewer(actor, role),
                result, reason, timestamp));

        return result;
    }


    public synchronized List<Decision> getDecisionsForRequest(String requestId) {
        requireText(requestId, "Request ID");
        return List.copyOf(decisions.getOrDefault(requestId, List.of()));
    }

    private synchronized void recordDecision(Decision decision) {
        decisions.computeIfAbsent(decision.requestId(), ignored -> new ArrayList<>())
                .add(decision);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank");
        }
    }
}