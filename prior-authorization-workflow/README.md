# Prior Authorization Workflow

A Java 21 and Maven project implementing a prior authorization lifecycle.

## Requirements

- JDK 21
- Maven Wrapper (included)

## Run tests on Windows PowerShell

```powershell
.\mvnw.cmd clean test
```

## Implemented requirements

- Authorization lifecycle with legal state transitions
- Intake-staff submission and role-based review permissions
- Nurse approval restricted to explicitly configured routine procedure codes
- Medical-director approval for non-routine procedures and medical-director-only denial
- Independent appeal review by a reviewer other than the original reviewer
- Low-risk automatic approval when required documentation is complete
- Automatic approvals create an approval record with configured validity dates and approved units
- Standard 14-business-day and urgent 72-elapsed-hour SLA timers
- SLA pause/resume while information is requested
- Weekend and configured-holiday handling for standard SLA time
- Escalation at 80% and breach alerts recorded in the audit log
- Closure when requested information is not received within the configured period
- Amendment history and SLA restart after amendment
- Duplicate detection by patient and procedure
- Appeal deadline enforcement against the recorded denial timestamp
- Append-only in-memory audit events with typed `AuditEntry` views
- Typed `AuthRequest`, `ClinicalDocument`, `Reviewer`, `Decision`, and `StateTransition` domain entities
- Approval validity windows and approved-unit consumption by claims

## Configure routine procedures

The task does not define a universal list of routine procedure codes. Supply the codes approved for nurse review when constructing `AuthorizationLifecycleWorkflow`:

```java
Set<String> routineProcedureCodes = Set.of("MRI-ROUTINE", "XRAY-ROUTINE");

AuthorizationLifecycleWorkflow workflow = new AuthorizationLifecycleWorkflow(
        submissionWorkflow,
        decisionWorkflow,
        transitionService,
        autoApprovalService,
        infoRequestClosureService,
        businessCalendar,
        ZoneId.of("UTC"),
        routineProcedureCodes);
```

Any code not explicitly listed is treated as non-routine for nurse approval. A medical director can approve non-routine requests; only a medical director can deny.

## Configure automatic approval terms

An eligible low-risk request must include validity dates and approved units. Configure these on the request:

```java
AuthorizationRequest request = new AuthorizationRequest(
        requestId,
        procedureCode,
        RequestPriority.STANDARD,
        submittedAt,
        requiredDocuments,
        new AutoApprovalTerms(effectiveAt, expiresAt, approvedUnits));
```

If the request is eligible for automatic approval but has no terms, the workflow rejects the automatic approval rather than creating an approval without a validity window or unit limit. Claims can then consume units through the created `ApprovedAuthorization` record.

## Core project types

- `AuthRequest` is the core request contract, implemented by `AuthorizationRequest`.
- `ClinicalDocument` represents a document attached to a request.
- `Reviewer` identifies a reviewer and their role.
- `Decision` stores the decision outcome, reviewer, reason, and time.
- `SlaTimer` tracks active SLA time and pause/resume behavior.
- `StateTransition` records each lifecycle status change.
- `AuditEntry` provides a typed view of append-only audit events.

## Project structure

- `src/main/java/com/priorauth/workflow/`: application and domain code
- `src/test/java/com/priorauth/workflow/`: automated tests
- `pom.xml`: Maven configuration

The project contains 167 test methods. Run the Maven command above on your machine to verify the full JUnit suite.

## Worked SLA example

Consider a standard-priority MRI authorization with a 14-business-day SLA.

1. The request accumulates 3 active business days of review time.
2. The reviewer requests additional information, pausing the SLA timer.
3. The clinic returns the requested documents on calendar day 7. The 4-day waiting period does not count toward active review time.
4. Review resumes with 3 of the 14 business days used, leaving 11 business days.
5. The 80% escalation threshold is 11.2 active business days (14 × 0.80). After resumption, another 8.2 active business days are needed to reach it.

The actual calendar date depends on weekends, configured holidays, the time of day, and the configured time zone. The timer measures accumulated active review time, not calendar time alone.
