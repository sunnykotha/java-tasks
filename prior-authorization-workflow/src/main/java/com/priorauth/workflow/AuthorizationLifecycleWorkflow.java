package com.priorauth.workflow;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.List;

public final class AuthorizationLifecycleWorkflow {
    private final AuthorizationSubmissionWorkflow submissionWorkflow;
    private final AuthorizationDecisionWorkflow decisionWorkflow;
    private final StateTransitionService transitions;
    private final AutoApprovalService autoApproval;
    private final InfoRequestClosureService infoClosure;
    private final BusinessCalendar calendar;
    private final ZoneId zoneId;
    private final AuditLog lifecycleAuditLog;
    private final ProcedureReviewPolicy procedureReviewPolicy;

    private final Map<String, AuthorizationRequest> requests = new HashMap<>();
    private final Map<String, AuthStatus> statuses = new HashMap<>();
    private final Map<String, SlaTimer> timers = new HashMap<>();
    private final Map<String, SlaAlertReporter> reporters = new HashMap<>();
    private final Map<String, RequestAmendmentTracker> amendments = new HashMap<>();
    private final Map<String, AppealDeadlinePolicy> appealPolicies = new HashMap<>();
    private final Map<String, String> originalReviewers = new HashMap<>();
    private final Map<String, Instant> denialTimestamps = new HashMap<>();
    private final Map<String, Instant> informationRequestedTimestamps = new HashMap<>();
    private final Map<String, List<StateTransition>> stateTransitions = new HashMap<>();

    public AuthorizationLifecycleWorkflow(
            AuthorizationSubmissionWorkflow submissionWorkflow,
            AuthorizationDecisionWorkflow decisionWorkflow,
            StateTransitionService transitions) {
        this(submissionWorkflow, decisionWorkflow, transitions,
                new AutoApprovalService(Set.of(), new AuditLog()),
                new InfoRequestClosureService(new AuditLog()),
                new BusinessCalendar(Set.of()), ZoneId.of("UTC"),
                new AuditLog(), Set.of());
    }

    public AuthorizationLifecycleWorkflow(
            AuthorizationSubmissionWorkflow submissionWorkflow,
            AuthorizationDecisionWorkflow decisionWorkflow,
            StateTransitionService transitions,
            AutoApprovalService autoApproval,
            InfoRequestClosureService infoClosure) {
        this(submissionWorkflow, decisionWorkflow, transitions,
                autoApproval, infoClosure, new BusinessCalendar(Set.of()),
                ZoneId.of("UTC"), new AuditLog(), Set.of());
    }

    public AuthorizationLifecycleWorkflow(
            AuthorizationSubmissionWorkflow submissionWorkflow,
            StateTransitionService transitions,
            Set<String> lowRiskCodes,
            AuditLog auditLog) {
        this(submissionWorkflow,
                new AuthorizationDecisionWorkflow(new DecisionService(auditLog)),
                transitions,
                new AutoApprovalService(lowRiskCodes, auditLog, transitions),
                new InfoRequestClosureService(auditLog),
                new BusinessCalendar(Set.of()), ZoneId.of("UTC"), auditLog, Set.of());
    }

    public AuthorizationLifecycleWorkflow(
            AuthorizationSubmissionWorkflow submissionWorkflow,
            AuthorizationDecisionWorkflow decisionWorkflow,
            StateTransitionService transitions,
            AutoApprovalService autoApproval,
            InfoRequestClosureService infoClosure,
            BusinessCalendar calendar,
            ZoneId zoneId) {
        this(submissionWorkflow, decisionWorkflow, transitions,
                autoApproval, infoClosure, calendar, zoneId, new AuditLog(), Set.of());
    }

    public AuthorizationLifecycleWorkflow(
            AuthorizationSubmissionWorkflow submissionWorkflow,
            AuthorizationDecisionWorkflow decisionWorkflow,
            StateTransitionService transitions,
            AutoApprovalService autoApproval,
            InfoRequestClosureService infoClosure,
            BusinessCalendar calendar,
            ZoneId zoneId,
            Set<String> routineProcedureCodes) {
        this(submissionWorkflow, decisionWorkflow, transitions, autoApproval,
                infoClosure, calendar, zoneId, new AuditLog(), routineProcedureCodes);
    }

    private AuthorizationLifecycleWorkflow(
            AuthorizationSubmissionWorkflow submissionWorkflow,
            AuthorizationDecisionWorkflow decisionWorkflow,
            StateTransitionService transitions,
            AutoApprovalService autoApproval,
            InfoRequestClosureService infoClosure,
            BusinessCalendar calendar,
            ZoneId zoneId,
            AuditLog lifecycleAuditLog,
            Set<String> routineProcedureCodes) {
        this.submissionWorkflow = Objects.requireNonNull(submissionWorkflow);
        this.decisionWorkflow = Objects.requireNonNull(decisionWorkflow);
        this.transitions = Objects.requireNonNull(transitions);
        this.autoApproval = Objects.requireNonNull(autoApproval);
        this.infoClosure = Objects.requireNonNull(infoClosure);
        this.calendar = Objects.requireNonNull(calendar);
        this.zoneId = Objects.requireNonNull(zoneId);
        this.lifecycleAuditLog = Objects.requireNonNull(lifecycleAuditLog);
        this.procedureReviewPolicy = new ProcedureReviewPolicy(routineProcedureCodes);
    }

    public synchronized String submit(
            AuthorizationRequest request, String patientId, String episodeId,
            String actor, ReviewerRole role) {
        if (actor == null || actor.isBlank() || role != ReviewerRole.INTAKE_STAFF) {
            throw new SecurityException("Only intake staff may submit requests");
        }
        Objects.requireNonNull(request, "Request is required");
        String id = request.getId();
        if (requests.containsKey(id)) {
            throw new IllegalStateException("Request ID already tracked: " + id);
        }
        if (submissionWorkflow.isDuplicate(
                patientId, request.getProcedureCode(), episodeId)) {
            throw new IllegalStateException("Duplicate authorization request");
        }

        String submittedId = submissionWorkflow.submit(
                id, patientId, request.getProcedureCode(), episodeId);
        requests.put(id, request);
        changeStatus(id, AuthStatus.SUBMITTED, actor, request.getSubmittedAt());
        SlaTimer timer = new SlaTimer(
                request.getPriority(), request.getSubmittedAt(), calendar, zoneId);
        timers.put(id, timer);
        reporters.put(id, new SlaAlertReporter(
                id, "SLA_MONITOR", timer, new SlaMonitor(), lifecycleAuditLog));
        amendments.put(id, new RequestAmendmentTracker(
                id, request.getSubmittedAt(), lifecycleAuditLog));
        return submittedId;
    }

    public synchronized AuthStatus beginReview(
            String requestId, String actor, ReviewerRole role) {
        return beginReview(requestId, actor, role, Instant.now());
    }

    public synchronized AuthStatus beginReview(
            String requestId, String actor, ReviewerRole role, Instant timestamp) {
        requireRequest(requestId);
        AuthStatus current = statuses.get(requestId);
        transitions.validateTransition(current, AuthStatus.UNDER_REVIEW);
        if (actor == null || actor.isBlank()) {
            throw new SecurityException("Reviewer identity is required");
        }
        Objects.requireNonNull(timestamp, "Timestamp is required");
        new AuthorizationPermissionService().validatePermission(
                role, current, AuthStatus.UNDER_REVIEW, actor, null);
        changeStatus(requestId, AuthStatus.UNDER_REVIEW, actor, timestamp);
        return AuthStatus.UNDER_REVIEW;
    }

    public synchronized AuthStatus decide(
            String requestId, String actor, ReviewerRole role,
            boolean approve, String reason, Instant decisionAt,
            Instant effectiveAt, Instant expiresAt, int approvedUnits) {
        requireRequest(requestId);
        AuthStatus current = statuses.get(requestId);
        transitions.validateTransition(
                current, approve ? AuthStatus.APPROVED : AuthStatus.DENIED);
        if (approve && role == ReviewerRole.NURSE_REVIEWER
                && !procedureReviewPolicy.isRoutine(
                        requireRequest(requestId).getProcedureCode())) {
            throw new SecurityException(
                    "Nurse reviewers may approve routine procedures only");
        }

        AuthStatus result = decisionWorkflow.decide(
                requestId, actor, role, current, approve, reason,
                decisionAt, effectiveAt, expiresAt, approvedUnits);
        changeStatus(requestId, result, actor, decisionAt);
        originalReviewers.put(requestId, actor);
        if (result == AuthStatus.DENIED) {
            denialTimestamps.put(requestId, decisionAt);
        }
        return result;
    }

    public synchronized AuthStatus evaluateAutomaticApproval(
            String requestId, Instant timestamp) {
        AuthorizationRequest request = requireRequest(requestId);
        AuthStatus current = statuses.get(requestId);
        if (autoApproval.isEligible(request, current)
                && !request.hasAutoApprovalTerms()) {
            throw new IllegalStateException(
                    "Eligible automatic approvals require validity dates and approved units");
        }
        AuthStatus result = autoApproval.evaluate(request, current, timestamp);
        if (result != current) {
            transitions.validateAutomaticApprovalTransition(current, result);
            decisionWorkflow.registerAutomaticApproval(
                    requestId, request.getAutoApprovalTerms());
            changeStatus(requestId, result, "AUTO_APPROVAL", timestamp);
        }
        return result;
    }

    public synchronized AuthStatus requestInformation(String requestId, Instant pauseAt, String actor, ReviewerRole role) {
        requireRequest(requestId);
        AuthStatus current = statuses.get(requestId);
        transitions.validateTransition(current, AuthStatus.INFO_REQUESTED);
        if (actor == null || actor.isBlank()) { throw new SecurityException("Reviewer identity is required"); }
        new AuthorizationPermissionService().validatePermission(role, current, AuthStatus.INFO_REQUESTED, actor, null);
        timers.get(requestId).pause(pauseAt);
        informationRequestedTimestamps.put(requestId, pauseAt);
        changeStatus(requestId, AuthStatus.INFO_REQUESTED, actor, pauseAt);
        return AuthStatus.INFO_REQUESTED;
    }

    public synchronized boolean receiveDocument(
            String requestId, String document, Instant receivedAt) {
        return receiveDocument(requestId,
                new ClinicalDocument(document, null, receivedAt));
    }

    public synchronized boolean receiveDocument(
            String requestId, ClinicalDocument document) {
        Objects.requireNonNull(document, "Clinical document is required");
        AuthorizationRequest request = requireRequest(requestId);
        if (statuses.get(requestId) == AuthStatus.INFO_REQUESTED
                && document.receivedAt() == null) {
            throw new IllegalArgumentException(
                    "Received timestamp is required while resuming an SLA");
        }
        if (statuses.get(requestId) == AuthStatus.INFO_REQUESTED) {
            transitions.validateTransition(
                    AuthStatus.INFO_REQUESTED, AuthStatus.UNDER_REVIEW);
            timers.get(requestId).resume(document.receivedAt());
        }
        request.receiveDocument(document);
        if (statuses.get(requestId) == AuthStatus.INFO_REQUESTED) {
            changeStatus(requestId, AuthStatus.UNDER_REVIEW,
                    "DOCUMENT_RECEIPT", document.receivedAt());
        }
        return request.getReceivedDocuments().contains(document.documentType());
    }

    public synchronized AuthStatus evaluateInformationRequestClosure(
            String requestId, String actor, Instant requestedAt,
            int allowedDays, Instant now) {
        requireRequest(requestId);
        AuthStatus current = statuses.get(requestId);
        Instant actualRequestedAt = informationRequestedTimestamps.get(requestId);
        if (current == AuthStatus.INFO_REQUESTED
                && !Objects.equals(actualRequestedAt, requestedAt)) {
            throw new IllegalArgumentException(
                    "Information-request timestamp does not match the recorded request");
        }
        AuthStatus result = infoClosure.evaluate(
                requestId, actor, current, requestedAt, allowedDays, now);
        if (result != current) {
            transitions.validateTransition(current, result);
            changeStatus(requestId, result, actor, now);
        }
        return result;
    }

    public synchronized RequestAmendmentTracker.Amendment amendRequest(
            String requestId, String actor, String reason, Instant timestamp) {
        requireRequest(requestId);
        RequestAmendmentTracker.Amendment amendment =
                amendments.get(requestId).amend(actor, reason, timestamp);
        timers.get(requestId).restart(timestamp);
        return amendment;
    }

    public synchronized AuthStatus submitAppeal(
            String requestId, String actor, Instant denialAt,
            Instant appealAt, Duration appealWindow) {
        requireRequest(requestId);
        if (statuses.get(requestId) != AuthStatus.DENIED) {
            throw new IllegalStateException("Only denied requests can be appealed");
        }
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("Appeal actor is required");
        }
        Instant actualDenialAt = denialTimestamps.get(requestId);
        if (actualDenialAt == null || !actualDenialAt.equals(denialAt)) {
            throw new IllegalArgumentException(
                    "Denial timestamp does not match the recorded decision");
        }
        AppealDeadlinePolicy policy =
                new AppealDeadlinePolicy(actualDenialAt, appealWindow);
        policy.requireAppealAllowedAt(appealAt);
        transitions.validateTransition(AuthStatus.DENIED, AuthStatus.APPEALED);
        appealPolicies.put(requestId, policy);
        changeStatus(requestId, AuthStatus.APPEALED, actor, appealAt);
        lifecycleAuditLog.record(
                requestId, appealAt, actor, "APPEAL_FILED", "Appeal submitted");
        return AuthStatus.APPEALED;
    }

    public synchronized AuthStatus beginAppealReview(
            String requestId, String actor, ReviewerRole role,
            Instant timestamp) {
        requireRequest(requestId);
        AuthStatus current = statuses.get(requestId);
        transitions.validateTransition(current, AuthStatus.APPEAL_REVIEW);
        Objects.requireNonNull(timestamp, "Timestamp is required");
        new AuthorizationPermissionService().validatePermission(
                role, current, AuthStatus.APPEAL_REVIEW, actor,
                originalReviewers.get(requestId));
        lifecycleAuditLog.record(
                requestId, timestamp, actor,
                "APPEAL_REVIEW_STARTED", "Independent appeal review started");
        changeStatus(requestId, AuthStatus.APPEAL_REVIEW, actor, timestamp);
        return AuthStatus.APPEAL_REVIEW;
    }

    public synchronized AuthStatus resolveAppeal(
            String requestId, String actor, ReviewerRole role,
            boolean overturn, String reason, Instant timestamp) {
        requireRequest(requestId);
        AuthStatus current = statuses.get(requestId);
        if (current != AuthStatus.APPEAL_REVIEW) {
            throw new IllegalStateException(
                    "Appeal must enter review before resolution");
        }
        AuthStatus result = decisionWorkflow.reviewAppeal(
                requestId, actor, role, originalReviewers.get(requestId),
                current, overturn, reason, timestamp);
        transitions.validateTransition(current, result);
        changeStatus(requestId, result, actor, timestamp);
        return result;
    }

    public synchronized SlaMonitor.Assessment checkSla(
            String requestId, Instant timestamp) {
        requireRequest(requestId);
        return reporters.get(requestId).check(timestamp);
    }

    public synchronized AuthorizationRequest getRequest(String requestId) {
        return requireRequest(requestId);
    }

    public synchronized AuthStatus getStatus(String requestId) {
        requireRequest(requestId);
        return statuses.get(requestId);
    }

    public synchronized SlaTimer getSlaTimer(String requestId) {
        requireRequest(requestId);
        return timers.get(requestId);
    }

    public synchronized RequestAmendmentTracker getAmendmentTracker(
            String requestId) {
        requireRequest(requestId);
        return amendments.get(requestId);
    }

    public synchronized AppealDeadlinePolicy getAppealDeadlinePolicy(
            String requestId) {
        requireRequest(requestId);
        AppealDeadlinePolicy policy = appealPolicies.get(requestId);
        if (policy == null) {
            throw new IllegalArgumentException("No appeal policy for " + requestId);
        }
        return policy;
    }

    public synchronized java.util.Optional<ApprovedAuthorization> findApproval(
            String requestId) {
        requireRequest(requestId);
        return decisionWorkflow.findApproval(requestId);
    }

    public synchronized void consumeClaim(
            String requestId, String claimId, int units, Instant serviceAt) {
        requireRequest(requestId);
        decisionWorkflow.consumeClaim(requestId, claimId, units, serviceAt);
    }

    public synchronized boolean isDuplicate(
            String patientId, String procedureCode, String episodeId) {
        return submissionWorkflow.isDuplicate(
                patientId, procedureCode, episodeId);
    }

    public synchronized int getSubmittedCount() {
        return submissionWorkflow.getSubmittedCount();
    }

    public synchronized int getTrackedRequestCount() {
        return requests.size();
    }

    public synchronized List<StateTransition> getStateTransitions(
            String requestId) {
        requireRequest(requestId);
        return List.copyOf(stateTransitions.getOrDefault(requestId, List.of()));
    }

    public synchronized List<Decision> getDecisions(String requestId) {
        requireRequest(requestId);
        return decisionWorkflow.getDecisionsForRequest(requestId);
    }

    public synchronized List<AuditLog.AuditEvent> getLifecycleAuditEvents(
            String requestId) {
        requireRequest(requestId);
        return lifecycleAuditLog.getEventsForRequest(requestId);
    }

    private void changeStatus(
            String requestId, AuthStatus next, String actor, Instant timestamp) {
        AuthStatus previous = statuses.put(requestId, next);
        stateTransitions.computeIfAbsent(requestId, ignored -> new java.util.ArrayList<>())
                .add(new StateTransition(requestId, previous, next, actor, timestamp));
    }

    private AuthorizationRequest requireRequest(String requestId) {
        AuthorizationRequest request = requests.get(requestId);
        if (request == null) {
            throw new IllegalArgumentException("Unknown request: " + requestId);
        }
        return request;
    }
}