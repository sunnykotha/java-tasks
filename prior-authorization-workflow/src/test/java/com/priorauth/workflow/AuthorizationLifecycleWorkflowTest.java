package com.priorauth.workflow;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.Duration;
import java.util.Set;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationLifecycleWorkflowTest {

    private static final Instant SUBMITTED =
            Instant.parse("2026-10-20T09:00:00Z");
    private static final Instant DECIDED =
            Instant.parse("2026-10-20T10:00:00Z");
    private static final Instant EFFECTIVE =
            Instant.parse("2026-10-20T00:00:00Z");
    private static final Instant EXPIRES =
            Instant.parse("2027-10-20T00:00:00Z");

    private AuthorizationLifecycleWorkflow createWorkflow() {
        AuditLog auditLog = new AuditLog();
        return new AuthorizationLifecycleWorkflow(
                new AuthorizationSubmissionWorkflow(
                        new DuplicateRequestDetector()),
                new AuthorizationDecisionWorkflow(
                        new DecisionService(auditLog)),
                new StateTransitionService(),
                new AutoApprovalService(Set.of(), auditLog),
                new InfoRequestClosureService(auditLog),
                new BusinessCalendar(Set.of()), ZoneId.of("UTC"),
                Set.of("MRI", "MRI-LOW-RISK"));
    }

    private AuthorizationLifecycleWorkflow createWorkflow(
            Set<String> lowRiskCodes, AuditLog auditLog) {
        return new AuthorizationLifecycleWorkflow(
                new AuthorizationSubmissionWorkflow(
                        new DuplicateRequestDetector()),
                new AuthorizationDecisionWorkflow(
                        new DecisionService(auditLog)),
                new StateTransitionService(),
                new AutoApprovalService(lowRiskCodes, auditLog),
                new InfoRequestClosureService(auditLog),
                new BusinessCalendar(Set.of()), ZoneId.of("UTC"),
                Set.of("MRI", "MRI-LOW-RISK"));
    }

    private AuthorizationRequest request(String id, String procedure) {
        return new AuthorizationRequest(
                id, procedure, RequestPriority.STANDARD,
                SUBMITTED, Set.of("REPORT"));
    }

    @Test
    void submissionIsTrackedAndStartsAsSubmitted() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        AuthorizationRequest request = request("PA-LIFE-1", "MRI");

        workflow.submit(request, "PAT-1", "EP-1", "intake-staff", ReviewerRole.INTAKE_STAFF);

        assertSame(request, workflow.getRequest("PA-LIFE-1"));
        assertEquals(AuthStatus.SUBMITTED,
                workflow.getStatus("PA-LIFE-1"));
        assertEquals(1, workflow.getTrackedRequestCount());
    }

    @Test
    void duplicateSubmissionDoesNotCreateAnotherTrackedRequest() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        workflow.submit(request("PA-LIFE-2", "MRI"), "PAT-2", "EP-2", "intake-staff", ReviewerRole.INTAKE_STAFF);

        assertThrows(IllegalStateException.class, () ->
                workflow.submit(request("PA-LIFE-3", "MRI"), "PAT-2", "EP-2", "intake-staff", ReviewerRole.INTAKE_STAFF));

        assertEquals(1, workflow.getTrackedRequestCount());
        assertEquals(AuthStatus.SUBMITTED,
                workflow.getStatus("PA-LIFE-2"));
        assertThrows(IllegalArgumentException.class,
                () -> workflow.getStatus("PA-LIFE-3"));
    }

    @Test
    void requestMustEnterReviewBeforeDecision() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        workflow.submit(request("PA-LIFE-4", "MRI"), "PAT-4", "EP-4", "intake-staff", ReviewerRole.INTAKE_STAFF);

        assertThrows(IllegalStateException.class, () ->
                workflow.decide(
                        "PA-LIFE-4", "reviewer",
                        ReviewerRole.NURSE_REVIEWER, true, "Meets criteria",
                        DECIDED, EFFECTIVE, EXPIRES, 5));

        assertEquals(AuthStatus.SUBMITTED,
                workflow.getStatus("PA-LIFE-4"));
        assertEquals(AuthStatus.UNDER_REVIEW,
                workflow.beginReview("PA-LIFE-4", "intake-staff", ReviewerRole.INTAKE_STAFF));
    }

    @Test
    void approvalDecisionUpdatesLifecycleStatus() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        workflow.submit(request("PA-LIFE-5", "MRI"), "PAT-5", "EP-5", "intake-staff", ReviewerRole.INTAKE_STAFF);
        workflow.beginReview("PA-LIFE-5", "intake-staff", ReviewerRole.INTAKE_STAFF);

        AuthStatus result = workflow.decide(
                "PA-LIFE-5", "nurse-reviewer",
                ReviewerRole.NURSE_REVIEWER, true, "Meets criteria",
                DECIDED, EFFECTIVE, EXPIRES, 5);

        assertEquals(AuthStatus.APPROVED, result);
        assertEquals(AuthStatus.APPROVED,
                workflow.getStatus("PA-LIFE-5"));
    }


    @Test
    void nurseCannotApproveProcedureNotClassifiedAsRoutine() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        workflow.submit(request("PA-NON-ROUTINE", "COMPLEX-SURGERY"),
                "PAT-NON-ROUTINE", "EP-NON-ROUTINE",
                "intake-staff", ReviewerRole.INTAKE_STAFF);
        workflow.beginReview("PA-NON-ROUTINE", "nurse-reviewer",
                ReviewerRole.NURSE_REVIEWER);

        assertThrows(SecurityException.class, () -> workflow.decide(
                "PA-NON-ROUTINE", "nurse-reviewer",
                ReviewerRole.NURSE_REVIEWER, true, "Approve request",
                DECIDED, EFFECTIVE, EXPIRES, 5));
        assertEquals(AuthStatus.UNDER_REVIEW,
                workflow.getStatus("PA-NON-ROUTINE"));
    }

    @Test
    void medicalDirectorCanApproveProcedureNotClassifiedAsRoutine() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        workflow.submit(request("PA-DIRECTOR-APPROVAL", "COMPLEX-SURGERY"),
                "PAT-DIRECTOR-APPROVAL", "EP-DIRECTOR-APPROVAL",
                "intake-staff", ReviewerRole.INTAKE_STAFF);
        workflow.beginReview("PA-DIRECTOR-APPROVAL", "intake-staff",
                ReviewerRole.INTAKE_STAFF);

        assertEquals(AuthStatus.APPROVED, workflow.decide(
                "PA-DIRECTOR-APPROVAL", "medical-director",
                ReviewerRole.MEDICAL_DIRECTOR, true, "Approved after review",
                DECIDED, EFFECTIVE, EXPIRES, 5));
    }


    @Test
    void lifecycleExposesTypedStateTransitionsAndDecisionRecords() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        workflow.submit(request("PA-DOMAIN-1", "MRI"),
                "PAT-DOMAIN-1", "EP-DOMAIN-1",
                "intake-staff", ReviewerRole.INTAKE_STAFF);
        workflow.beginReview("PA-DOMAIN-1", "nurse-reviewer",
                ReviewerRole.NURSE_REVIEWER, DECIDED);
        workflow.decide("PA-DOMAIN-1", "nurse-reviewer",
                ReviewerRole.NURSE_REVIEWER, true, "Routine request approved",
                DECIDED, EFFECTIVE, EXPIRES, 4);

        List<StateTransition> transitions =
                workflow.getStateTransitions("PA-DOMAIN-1");
        assertEquals(3, transitions.size());
        assertEquals(AuthStatus.SUBMITTED, transitions.get(0).toStatus());
        assertEquals(AuthStatus.UNDER_REVIEW, transitions.get(1).toStatus());
        assertEquals(AuthStatus.APPROVED, transitions.get(2).toStatus());
        assertEquals("nurse-reviewer",
                workflow.getDecisions("PA-DOMAIN-1").get(0)
                        .reviewer().reviewerId());
    }

    @Test
    void clinicalDocumentsAreStoredAsTypedEntities() {
        AuthorizationRequest request = request("PA-DOC-ENTITY", "MRI");
        ClinicalDocument document = new ClinicalDocument(
                "REPORT", "docs/report-123", DECIDED);

        request.receiveDocument(document);

        assertTrue(request.isDocumentationComplete());
        assertEquals(document,
                request.getClinicalDocuments().get("REPORT"));
        AuthRequest coreRequest = request;
        assertEquals("PA-DOC-ENTITY", coreRequest.getId());
    }

    @Test
    void deniedDecisionUpdatesLifecycleStatus() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        workflow.submit(request("PA-LIFE-6", "MRI"), "PAT-6", "EP-6", "intake-staff", ReviewerRole.INTAKE_STAFF);
        workflow.beginReview("PA-LIFE-6", "intake-staff", ReviewerRole.INTAKE_STAFF);

        AuthStatus result = workflow.decide(
                "PA-LIFE-6", "medical-director",
                ReviewerRole.MEDICAL_DIRECTOR, false, "Criteria not met",
                DECIDED, EFFECTIVE, EXPIRES, 0);

        assertEquals(AuthStatus.DENIED, result);
        assertEquals(AuthStatus.DENIED,
                workflow.getStatus("PA-LIFE-6"));
    }

    @Test
    void unknownRequestCannotEnterReview() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        assertThrows(IllegalArgumentException.class,
                () -> workflow.beginReview("MISSING", "intake-staff", ReviewerRole.INTAKE_STAFF));
    }

    @Test
    void eligibleRequestAutoApprovesAndUpdatesTrackedStatus() {
        AuditLog auditLog = new AuditLog();
        AuthorizationLifecycleWorkflow workflow =
                createWorkflow(Set.of("MRI-LOW-RISK"), auditLog);
        AuthorizationRequest request = new AuthorizationRequest(
                "PA-LIFE-7", "MRI-LOW-RISK", RequestPriority.STANDARD,
                SUBMITTED, Set.of("REPORT"),
                new AutoApprovalTerms(EFFECTIVE, EXPIRES, 5));
        request.receiveDocument("REPORT");
        workflow.submit(request, "PAT-7", "EP-7", "intake-staff", ReviewerRole.INTAKE_STAFF);

        assertEquals(AuthStatus.APPROVED,
                workflow.evaluateAutomaticApproval("PA-LIFE-7", DECIDED));
        assertEquals(AuthStatus.APPROVED,
                workflow.getStatus("PA-LIFE-7"));
        assertEquals("AUTO_APPROVED",
                auditLog.getEventsForRequest("PA-LIFE-7").get(0).eventType());
        ApprovedAuthorization approval = workflow.findApproval("PA-LIFE-7")
                .orElseThrow();
        assertEquals(EFFECTIVE, approval.getEffectiveAt());
        assertEquals(EXPIRES, approval.getExpiresAt());
        assertEquals(5, approval.getApprovedUnits());
        workflow.consumeClaim("PA-LIFE-7", "CLAIM-AUTO-1", 2, DECIDED);
        assertEquals(3, approval.getRemainingUnits());
    }


    @Test
    void eligibleAutomaticApprovalRequiresValidityAndUnitTerms() {
        AuditLog auditLog = new AuditLog();
        AuthorizationLifecycleWorkflow workflow =
                createWorkflow(Set.of("MRI-LOW-RISK"), auditLog);
        AuthorizationRequest request = request(
                "PA-AUTO-NO-TERMS", "MRI-LOW-RISK");
        request.receiveDocument("REPORT");
        workflow.submit(request, "PAT-AUTO-NO-TERMS", "EP-AUTO-NO-TERMS",
                "intake-staff", ReviewerRole.INTAKE_STAFF);

        assertThrows(IllegalStateException.class,
                () -> workflow.evaluateAutomaticApproval(
                        "PA-AUTO-NO-TERMS", DECIDED));
        assertEquals(AuthStatus.SUBMITTED,
                workflow.getStatus("PA-AUTO-NO-TERMS"));
        assertTrue(workflow.findApproval("PA-AUTO-NO-TERMS").isEmpty());
        assertTrue(auditLog.getEventsForRequest("PA-AUTO-NO-TERMS").isEmpty());
    }

    @Test
    void autoApprovalWaitsForCompleteDocumentation() {
        AuthorizationLifecycleWorkflow workflow =
                createWorkflow(Set.of("MRI-LOW-RISK"), new AuditLog());
        workflow.submit(request("PA-LIFE-8", "MRI-LOW-RISK"), "PAT-8", "EP-8", "intake-staff", ReviewerRole.INTAKE_STAFF);

        assertEquals(AuthStatus.SUBMITTED,
                workflow.evaluateAutomaticApproval("PA-LIFE-8", DECIDED));
        assertEquals(AuthStatus.SUBMITTED,
                workflow.getStatus("PA-LIFE-8"));
    }

    @Test
    void informationRequestRemainsOpenBeforeDeadline() {
        AuditLog auditLog = new AuditLog();
        AuthorizationLifecycleWorkflow workflow =
                createWorkflow(Set.of(), auditLog);
        workflow.submit(request("PA-LIFE-9", "MRI"), "PAT-9", "EP-9", "intake-staff", ReviewerRole.INTAKE_STAFF);
        workflow.beginReview("PA-LIFE-9", "intake-staff", ReviewerRole.INTAKE_STAFF);
        Instant requestedAt = Instant.parse("2026-10-20T10:00:00Z");
        workflow.requestInformation("PA-LIFE-9", requestedAt,
                "nurse-reviewer", ReviewerRole.NURSE_REVIEWER);

        Instant beforeDeadline = Instant.parse("2026-10-24T09:59:59Z");

        assertEquals(AuthStatus.INFO_REQUESTED,
                workflow.evaluateInformationRequestClosure(
                        "PA-LIFE-9", "intake", requestedAt, 4,
                        beforeDeadline));
        assertEquals(AuthStatus.INFO_REQUESTED,
                workflow.getStatus("PA-LIFE-9"));
        assertTrue(auditLog.getEventsForRequest("PA-LIFE-9").isEmpty());
    }

    @Test
    void overdueInformationRequestClosesAndUpdatesTrackedStatus() {
        AuditLog auditLog = new AuditLog();
        AuthorizationLifecycleWorkflow workflow =
                createWorkflow(Set.of(), auditLog);
        workflow.submit(request("PA-LIFE-10", "MRI"), "PAT-10", "EP-10", "intake-staff", ReviewerRole.INTAKE_STAFF);
        workflow.beginReview("PA-LIFE-10", "intake-staff", ReviewerRole.INTAKE_STAFF);
        Instant requestedAt = Instant.parse("2026-10-20T10:00:00Z");
        workflow.requestInformation("PA-LIFE-10", requestedAt,
                "nurse-reviewer", ReviewerRole.NURSE_REVIEWER);

        Instant deadline = Instant.parse("2026-10-24T10:00:00Z");

        assertEquals(AuthStatus.CLOSED_INSUFFICIENT_INFO,
                workflow.evaluateInformationRequestClosure(
                        "PA-LIFE-10", "intake", requestedAt, 4, deadline));
        assertEquals(AuthStatus.CLOSED_INSUFFICIENT_INFO,
                workflow.getStatus("PA-LIFE-10"));
        assertEquals("CLOSED_INSUFFICIENT_INFO",
                auditLog.getEventsForRequest("PA-LIFE-10")
                        .get(0).eventType());
    }

    @Test
    void informationClosureDoesNotCloseRequestInWrongState() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        workflow.submit(request("PA-LIFE-11", "MRI"), "PAT-11", "EP-11", "intake-staff", ReviewerRole.INTAKE_STAFF);

        assertEquals(AuthStatus.SUBMITTED,
                workflow.evaluateInformationRequestClosure(
                        "PA-LIFE-11", "intake", SUBMITTED, 4,
                        Instant.parse("2026-10-30T00:00:00Z")));
        assertEquals(AuthStatus.SUBMITTED,
                workflow.getStatus("PA-LIFE-11"));
    }

    @Test
    void amendmentUpdatesRevisionAndReviewStart() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        workflow.submit(request("PA-LIFE-12", "MRI"), "PAT-12", "EP-12", "intake-staff", ReviewerRole.INTAKE_STAFF);
        Instant amendedAt = Instant.parse("2026-10-21T09:00:00Z");

        RequestAmendmentTracker.Amendment amendment =
                workflow.amendRequest(
                        "PA-LIFE-12", "intake", "Corrected documentation",
                        amendedAt);

        assertEquals(1, amendment.revision());
        assertEquals(amendedAt, amendment.timestamp());
        SlaTimer timer = workflow.getSlaTimer("PA-LIFE-12");
        assertEquals(1L, timer.getElapsed(amendedAt.plusSeconds(3600)).toHours());
        assertEquals(1,
                workflow.getAmendmentTracker("PA-LIFE-12").getRevision());
        assertEquals(amendedAt,
                workflow.getAmendmentTracker("PA-LIFE-12")
                        .getReviewStartedAt());
    }

    @Test
    void amendmentCannotPrecedeSubmission() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        workflow.submit(request("PA-LIFE-13", "MRI"), "PAT-13", "EP-13", "intake-staff", ReviewerRole.INTAKE_STAFF);

        assertThrows(IllegalArgumentException.class, () ->
                workflow.amendRequest(
                        "PA-LIFE-13", "intake", "Correction",
                        Instant.parse("2026-10-19T09:00:00Z")));

        assertEquals(0,
                workflow.getAmendmentTracker("PA-LIFE-13").getRevision());
    }

    @Test
    void deniedRequestCanBeAppealedWithinDeadline() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        workflow.submit(request("PA-LIFE-14", "MRI"), "PAT-14", "EP-14", "intake-staff", ReviewerRole.INTAKE_STAFF);
        workflow.beginReview("PA-LIFE-14", "intake-staff", ReviewerRole.INTAKE_STAFF);
        workflow.decide(
                "PA-LIFE-14", "medical-director",
                ReviewerRole.MEDICAL_DIRECTOR, false, "Criteria not met",
                DECIDED, EFFECTIVE, EXPIRES, 0);

        Instant appealAt = DECIDED.plus(Duration.ofDays(10));
        assertEquals(AuthStatus.APPEALED,
                workflow.submitAppeal(
                        "PA-LIFE-14", "patient-representative",
                        DECIDED, appealAt, Duration.ofDays(30)));
        assertEquals(DECIDED.plus(Duration.ofDays(30)),
                workflow.getAppealDeadlinePolicy("PA-LIFE-14").getDeadline());
    }

    @Test
    void appealAfterDeadlineIsRejectedWithoutChangingStatus() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        workflow.submit(request("PA-LIFE-15", "MRI"), "PAT-15", "EP-15", "intake-staff", ReviewerRole.INTAKE_STAFF);
        workflow.beginReview("PA-LIFE-15", "intake-staff", ReviewerRole.INTAKE_STAFF);
        workflow.decide(
                "PA-LIFE-15", "medical-director",
                ReviewerRole.MEDICAL_DIRECTOR, false, "Criteria not met",
                DECIDED, EFFECTIVE, EXPIRES, 0);

        assertThrows(IllegalStateException.class, () ->
                workflow.submitAppeal(
                        "PA-LIFE-15", "patient-representative",
                        DECIDED, DECIDED.plus(Duration.ofDays(31)),
                        Duration.ofDays(30)));

        assertEquals(AuthStatus.DENIED,
                workflow.getStatus("PA-LIFE-15"));
    }

    @Test
    void appealMustPassThroughReviewBeforeResolution() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        workflow.submit(request("PA-LIFE-16", "MRI"), "PAT-16", "EP-16", "intake-staff", ReviewerRole.INTAKE_STAFF);
        workflow.beginReview("PA-LIFE-16", "intake-staff", ReviewerRole.INTAKE_STAFF);
        workflow.decide(
                "PA-LIFE-16", "medical-director",
                ReviewerRole.MEDICAL_DIRECTOR, false, "Criteria not met",
                DECIDED, EFFECTIVE, EXPIRES, 0);
        workflow.submitAppeal(
                "PA-LIFE-16", "patient-representative",
                DECIDED, DECIDED.plusSeconds(60), Duration.ofDays(30));

        assertThrows(IllegalStateException.class, () ->
                workflow.resolveAppeal(
                        "PA-LIFE-16", "appeal-reviewer",
                        ReviewerRole.APPEAL_REVIEWER, true,
                        "New evidence", DECIDED.plusSeconds(120)));

        assertEquals(AuthStatus.APPEAL_REVIEW,
                workflow.beginAppealReview("PA-LIFE-16", "appeal-reviewer", ReviewerRole.APPEAL_REVIEWER, DECIDED.plusSeconds(120)));
        assertEquals(AuthStatus.OVERTURNED,
                workflow.resolveAppeal(
                        "PA-LIFE-16", "appeal-reviewer",
                        ReviewerRole.APPEAL_REVIEWER, true,
                        "New evidence supports approval",
                        DECIDED.plusSeconds(180)));
        assertEquals(AuthStatus.OVERTURNED,
                workflow.getStatus("PA-LIFE-16"));
    }

    @Test
    void lifecycleServicesShareOneAuditLog() {
        AuditLog sharedAuditLog = new AuditLog();
        AuthorizationLifecycleWorkflow workflow =
                new AuthorizationLifecycleWorkflow(
                        new AuthorizationSubmissionWorkflow(
                                new DuplicateRequestDetector()),
                        new StateTransitionService(),
                        Set.of("MRI-LOW-RISK"),
                        sharedAuditLog);

        AuthorizationRequest autoRequest = new AuthorizationRequest(
                "PA-AUDIT-1", "MRI-LOW-RISK", RequestPriority.STANDARD,
                SUBMITTED, Set.of("REPORT"),
                new AutoApprovalTerms(EFFECTIVE, EXPIRES, 5));
        autoRequest.receiveDocument("REPORT");
        workflow.submit(autoRequest, "PAT-AUDIT-1", "EP-AUDIT-1", "intake-staff", ReviewerRole.INTAKE_STAFF);

        assertEquals(AuthStatus.APPROVED,
                workflow.evaluateAutomaticApproval(
                        "PA-AUDIT-1", DECIDED));
        assertTrue(sharedAuditLog.getEventsForRequest("PA-AUDIT-1")
                .stream()
                .anyMatch(event ->
                        event.eventType().equals("AUTO_APPROVED")));

        workflow.submit(request("PA-AUDIT-2", "MRI"), "PAT-AUDIT-2", "EP-AUDIT-2", "intake-staff", ReviewerRole.INTAKE_STAFF);
        workflow.beginReview("PA-AUDIT-2", "intake-staff", ReviewerRole.INTAKE_STAFF);
        workflow.decide(
                "PA-AUDIT-2", "medical-director",
                ReviewerRole.MEDICAL_DIRECTOR, false, "Criteria not met",
                DECIDED, EFFECTIVE, EXPIRES, 0);
        workflow.submitAppeal(
                "PA-AUDIT-2", "patient-representative",
                DECIDED, DECIDED.plusSeconds(60), Duration.ofDays(30));

        assertTrue(sharedAuditLog.getEventsForRequest("PA-AUDIT-2")
                .stream()
                .anyMatch(event ->
                        event.eventType().equals("APPEAL_FILED")));
        assertTrue(sharedAuditLog.getEventsForRequest("PA-AUDIT-2")
                .stream()
                .anyMatch(event ->
                        event.eventType().equals("DENIED")
                        || event.eventType().equals("DECISION_DENIED")));
    }

    @Test
    void intakeStaffCannotResolveAppeal() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        workflow.submit(request("PA-LIFE-SEC-1", "MRI"), "PAT-SEC-1", "EP-SEC-1", "intake-staff", ReviewerRole.INTAKE_STAFF);
        workflow.beginReview("PA-LIFE-SEC-1", "intake-staff", ReviewerRole.INTAKE_STAFF);
        workflow.decide(
                "PA-LIFE-SEC-1", "medical-director",
                ReviewerRole.MEDICAL_DIRECTOR, false, "Criteria not met",
                DECIDED, EFFECTIVE, EXPIRES, 0);
        workflow.submitAppeal(
                "PA-LIFE-SEC-1", "patient-representative",
                DECIDED, DECIDED.plusSeconds(60), Duration.ofDays(30));
        workflow.beginAppealReview("PA-LIFE-SEC-1", "appeal-reviewer", ReviewerRole.APPEAL_REVIEWER, DECIDED.plusSeconds(90));

        assertThrows(SecurityException.class, () ->
                workflow.resolveAppeal(
                        "PA-LIFE-SEC-1", "intake",
                        ReviewerRole.INTAKE_STAFF, true,
                        "Attempted overturn", DECIDED.plusSeconds(120)));

        assertEquals(AuthStatus.APPEAL_REVIEW,
                workflow.getStatus("PA-LIFE-SEC-1"));
        assertTrue(workflow.getLifecycleAuditEvents("PA-LIFE-SEC-1")
                .stream()
                .noneMatch(event ->
                        event.eventType().equals("APPEAL_OVERTURNED")));
    }

    @Test
    void originalReviewerCannotResolveOwnAppeal() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        workflow.submit(request("PA-LIFE-SEC-2", "MRI"), "PAT-SEC-2", "EP-SEC-2", "intake-staff", ReviewerRole.INTAKE_STAFF);
        workflow.beginReview("PA-LIFE-SEC-2", "intake-staff", ReviewerRole.INTAKE_STAFF);
        workflow.decide(
                "PA-LIFE-SEC-2", "medical-director",
                ReviewerRole.MEDICAL_DIRECTOR, false, "Criteria not met",
                DECIDED, EFFECTIVE, EXPIRES, 0);
        workflow.submitAppeal(
                "PA-LIFE-SEC-2", "patient-representative",
                DECIDED, DECIDED.plusSeconds(60), Duration.ofDays(30));
        workflow.beginAppealReview("PA-LIFE-SEC-2", "appeal-reviewer", ReviewerRole.APPEAL_REVIEWER, DECIDED.plusSeconds(90));

        assertThrows(SecurityException.class, () ->
                workflow.resolveAppeal(
                        "PA-LIFE-SEC-2", "medical-director",
                        ReviewerRole.APPEAL_REVIEWER, true,
                        "Self-review attempt", DECIDED.plusSeconds(120)));

        assertEquals(AuthStatus.APPEAL_REVIEW,
                workflow.getStatus("PA-LIFE-SEC-2"));
        assertTrue(workflow.getLifecycleAuditEvents("PA-LIFE-SEC-2")
                .stream()
                .noneMatch(event ->
                        event.eventType().equals("APPEAL_OVERTURNED")));
    }

    @Test
    void slaTimerStartsAtSubmission() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        workflow.submit(request("PA-SLA-1", "MRI"), "PAT-SLA-1", "EP-SLA-1", "intake-staff", ReviewerRole.INTAKE_STAFF);

        SlaTimer timer = workflow.getSlaTimer("PA-SLA-1");

        assertEquals(Duration.ZERO, timer.getElapsed(SUBMITTED));
        assertEquals(Duration.ofDays(14), timer.getAllowedDuration());
    }

    @Test
    void lifecyclePausesAndResumesSlaAroundInformationRequest() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        workflow.submit(request("PA-SLA-2", "MRI"), "PAT-SLA-2", "EP-SLA-2", "intake-staff", ReviewerRole.INTAKE_STAFF);
        assertEquals(AuthStatus.UNDER_REVIEW, workflow.beginReview("PA-SLA-2", "intake-staff", ReviewerRole.INTAKE_STAFF));

        Instant pauseAt = SUBMITTED.plus(Duration.ofDays(3));
        assertEquals(AuthStatus.INFO_REQUESTED,
                workflow.requestInformation("PA-SLA-2", pauseAt, "nurse-reviewer", ReviewerRole.NURSE_REVIEWER));
        assertTrue(workflow.getSlaTimer("PA-SLA-2").isPaused());

        Instant documentsArrive = SUBMITTED.plus(Duration.ofDays(7));
        assertTrue(workflow.receiveDocument(
                "PA-SLA-2", "REPORT", documentsArrive));
        assertFalse(workflow.getSlaTimer("PA-SLA-2").isPaused());

        Instant twoDaysAfterResume = documentsArrive.plus(Duration.ofDays(2));
        assertEquals(
                Duration.ofDays(5),
                workflow.getSlaTimer("PA-SLA-2").getElapsed(twoDaysAfterResume));
    }

    @Test
    void lifecycleSlaCheckReportsEscalationAndBreachThresholds() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        workflow.submit(request("PA-SLA-3", "MRI"), "PAT-SLA-3", "EP-SLA-3", "intake-staff", ReviewerRole.INTAKE_STAFF);

        SlaMonitor.Assessment escalation = workflow.checkSla(
                "PA-SLA-3", SUBMITTED.plus(Duration.ofDays(16)));
        assertEquals(
                SlaMonitor.Status.ESCALATION_REQUIRED, escalation.status());

        SlaMonitor.Assessment breach = workflow.checkSla(
                "PA-SLA-3", SUBMITTED.plus(Duration.ofDays(20)));
        assertEquals(SlaMonitor.Status.BREACHED, breach.status());

        long escalations = workflow.getLifecycleAuditEvents("PA-SLA-3")
                .stream()
                .filter(event -> event.toString().contains("SLA_ESCALATION"))
                .count();
        long breaches = workflow.getLifecycleAuditEvents("PA-SLA-3")
                .stream()
                .filter(event -> event.toString().contains("SLA_BREACH"))
                .count();

        assertEquals(1, escalations);
        assertEquals(1, breaches);
    }

    @Test
    void configuredHolidayIsExcludedFromLifecycleSlaTimer() {
        BusinessCalendar calendar = new BusinessCalendar(
                Set.of(LocalDate.of(2026, 10, 21)));

        AuditLog auditLog = new AuditLog();
        AuthorizationLifecycleWorkflow workflow =
                new AuthorizationLifecycleWorkflow(
                        new AuthorizationSubmissionWorkflow(
                                new DuplicateRequestDetector()),
                        new AuthorizationDecisionWorkflow(
                                new DecisionService(auditLog)),
                        new StateTransitionService(),
                        new AutoApprovalService(Set.of(), auditLog),
                        new InfoRequestClosureService(auditLog),
                        calendar,
                        ZoneId.of("UTC"));

        workflow.submit(request("PA-HOLIDAY-SLA", "MRI"), "PAT-HOLIDAY", "EP-HOLIDAY", "intake-staff", ReviewerRole.INTAKE_STAFF);

        SlaTimer timer = workflow.getSlaTimer("PA-HOLIDAY-SLA");
        Instant checkAt = Instant.parse("2026-10-22T09:00:00Z");

        assertEquals(Duration.ofHours(24), timer.getElapsed(checkAt));
    }

    @Test
    void onlyIntakeStaffCanSubmitAuthorizationRequests() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        assertThrows(SecurityException.class, () -> workflow.submit(request("PA-ROLE-1", "MRI"), "PAT-ROLE-1", "EP-ROLE-1", "nurse-reviewer", ReviewerRole.NURSE_REVIEWER));
        assertEquals(0, workflow.getTrackedRequestCount());
        assertEquals("PA-ROLE-2", workflow.submit(request("PA-ROLE-2", "MRI"), "PAT-ROLE-2", "EP-ROLE-2", "intake-staff", ReviewerRole.INTAKE_STAFF));
    }

    @Test
    void unauthorizedActorCannotBeginReview() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        workflow.submit(request("PA-REVIEW-ROLE-1", "MRI"), "PAT-REVIEW-ROLE-1", "EP-REVIEW-ROLE-1", "intake-staff", ReviewerRole.INTAKE_STAFF);
        assertThrows(SecurityException.class, () -> workflow.beginReview("PA-REVIEW-ROLE-1", "unauthorized", null));
        assertEquals(AuthStatus.SUBMITTED, workflow.getStatus("PA-REVIEW-ROLE-1"));
        assertEquals(AuthStatus.UNDER_REVIEW, workflow.beginReview("PA-REVIEW-ROLE-1", "nurse-reviewer", ReviewerRole.NURSE_REVIEWER));
    }

    @Test
    void unauthorizedActorCannotRequestInformation() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        workflow.submit(request("PA-INFO-ROLE-1", "MRI"), "PAT-INFO-ROLE-1", "EP-INFO-ROLE-1", "intake-staff", ReviewerRole.INTAKE_STAFF);
        workflow.beginReview("PA-INFO-ROLE-1", "nurse-reviewer", ReviewerRole.NURSE_REVIEWER);
        Instant pauseAt = SUBMITTED.plus(Duration.ofDays(3));
        assertThrows(SecurityException.class, () -> workflow.requestInformation("PA-INFO-ROLE-1", pauseAt, "unauthorized", ReviewerRole.INTAKE_STAFF));
        assertEquals(AuthStatus.UNDER_REVIEW, workflow.getStatus("PA-INFO-ROLE-1"));
        assertEquals(Duration.ofDays(3), workflow.getSlaTimer("PA-INFO-ROLE-1").getElapsed(pauseAt));
        assertEquals(AuthStatus.INFO_REQUESTED, workflow.requestInformation("PA-INFO-ROLE-1", pauseAt, "nurse-reviewer", ReviewerRole.NURSE_REVIEWER));
    }

    @Test
    void onlyIndependentAppealReviewerCanBeginAppealReview() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        workflow.submit(request("PA-APPEAL-ROLE", "MRI"), "PAT-APPEAL-ROLE",
                "EP-APPEAL-ROLE", "intake-staff", ReviewerRole.INTAKE_STAFF);
        workflow.beginReview("PA-APPEAL-ROLE", "nurse-reviewer",
                ReviewerRole.NURSE_REVIEWER);
        workflow.decide("PA-APPEAL-ROLE", "medical-director",
                ReviewerRole.MEDICAL_DIRECTOR, false, "Not eligible",
                DECIDED, EFFECTIVE, EXPIRES, 0);
        workflow.submitAppeal("PA-APPEAL-ROLE", "patient",
                DECIDED, DECIDED.plusSeconds(60), Duration.ofDays(30));

        assertThrows(SecurityException.class, () ->
                workflow.beginAppealReview("PA-APPEAL-ROLE", "intake",
                        ReviewerRole.INTAKE_STAFF, DECIDED.plusSeconds(90)));
        assertEquals(AuthStatus.APPEALED, workflow.getStatus("PA-APPEAL-ROLE"));

        assertEquals(AuthStatus.APPEAL_REVIEW,
                workflow.beginAppealReview("PA-APPEAL-ROLE", "appeal-reviewer",
                        ReviewerRole.APPEAL_REVIEWER, DECIDED.plusSeconds(90)));
    }


    @Test
    void appealCannotUseCallerSuppliedDenialTimestampToExtendDeadline() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        workflow.submit(request("PA-APPEAL-TIME", "MRI"), "PAT-APPEAL-TIME",
                "EP-APPEAL-TIME", "intake-staff", ReviewerRole.INTAKE_STAFF);
        workflow.beginReview("PA-APPEAL-TIME", "nurse-reviewer",
                ReviewerRole.NURSE_REVIEWER);
        workflow.decide("PA-APPEAL-TIME", "medical-director",
                ReviewerRole.MEDICAL_DIRECTOR, false, "Not eligible",
                DECIDED, EFFECTIVE, EXPIRES, 0);

        assertThrows(IllegalArgumentException.class, () ->
                workflow.submitAppeal("PA-APPEAL-TIME", "patient",
                        DECIDED.minus(Duration.ofDays(30)),
                        DECIDED.plus(Duration.ofDays(31)),
                        Duration.ofDays(30)));
        assertEquals(AuthStatus.DENIED, workflow.getStatus("PA-APPEAL-TIME"));
    }

    @Test
    void informationClosureMustUseRecordedInformationRequestTimestamp() {
        AuthorizationLifecycleWorkflow workflow = createWorkflow();
        workflow.submit(request("PA-INFO-TIME", "MRI"), "PAT-INFO-TIME",
                "EP-INFO-TIME", "intake-staff", ReviewerRole.INTAKE_STAFF);
        workflow.beginReview("PA-INFO-TIME", "nurse-reviewer",
                ReviewerRole.NURSE_REVIEWER);
        Instant requestedAt = SUBMITTED.plus(Duration.ofDays(3));
        workflow.requestInformation("PA-INFO-TIME", requestedAt,
                "nurse-reviewer", ReviewerRole.NURSE_REVIEWER);

        assertThrows(IllegalArgumentException.class, () ->
                workflow.evaluateInformationRequestClosure(
                        "PA-INFO-TIME", "intake",
                        requestedAt.plus(Duration.ofDays(5)), 4,
                        requestedAt.plus(Duration.ofDays(10))));
        assertEquals(AuthStatus.INFO_REQUESTED,
                workflow.getStatus("PA-INFO-TIME"));
    }

}
