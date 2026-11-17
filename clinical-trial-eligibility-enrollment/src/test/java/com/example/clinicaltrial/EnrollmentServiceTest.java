package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EnrollmentServiceTest {

    private final EnrollmentService service = new EnrollmentService();

    private Patient patient(long id) {
        return new Patient(id, LocalDate.of(1990, 5, 15), Sex.FEMALE);
    }

    private Trial trial(long id, String name) {
        return new Trial(id, name, 100, 25);
    }

    private Screening eligibleScreening(Patient patient, Trial trial) {
        return new Screening(
                1L,
                patient,
                trial,
                new Site(1L, "Site A", trial),
                LocalDateTime.of(2026, 1, 10, 9, 0),
                true,
                List.of()
        );
    }

    private Enrollment existingEnrollment(
            Patient patient, Trial trial, EnrollmentStatus status
    ) {
        LocalDateTime enrolledAt =
                LocalDateTime.of(2026, 1, 1, 9, 0);

        return new Enrollment(
                10L,
                patient,
                trial,
                new Site(10L, "Existing Site", trial),
                new Arm(10L, "Existing Arm", trial, 50),
                enrolledAt,
                status,
                status == EnrollmentStatus.WITHDRAWN
                        ? enrolledAt.plusDays(1) : null
        );
    }

    private Enrollment enroll(
            Screening screening,
            Arm arm,
            List<Enrollment> existing,
            Set<Long> conflicts
    ) {
        return service.enroll(
                100L,
                screening,
                arm,
                LocalDateTime.of(2026, 1, 10, 10, 0),
                existing,
                conflicts
        );
    }

    @Test
    void enrollsEligiblePatient() {
        Patient patient = patient(1);
        Trial trial = trial(1, "Study A");

        Enrollment enrollment = enroll(
                eligibleScreening(patient, trial),
                new Arm(1L, "Arm A", trial, 50),
                List.of(),
                Set.of()
        );

        assertEquals(EnrollmentStatus.ACTIVE, enrollment.status());
        assertEquals(patient, enrollment.patient());
        assertEquals(trial, enrollment.trial());
    }

    @Test
    void rejectsIneligiblePatient() {
        Patient patient = patient(1);
        Trial trial = trial(1, "Study A");

        Screening failed = new Screening(
                1L, patient, trial, new Site(1L, "Site A", trial),
                LocalDateTime.of(2026, 1, 10, 9, 0),
                false, List.of("AGE")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> enroll(
                        failed, new Arm(1L, "Arm A", trial, 50),
                        List.of(), Set.of()
                )
        );
    }

    @Test
    void rejectsEnrollmentInWrongTrialArm() {
        Patient patient = patient(1);
        Trial trial = trial(1, "Study A");
        Trial other = trial(2, "Study B");

        assertThrows(
                IllegalArgumentException.class,
                () -> enroll(
                        eligibleScreening(patient, trial),
                        new Arm(2L, "Arm B", other, 20),
                        List.of(), Set.of()
                )
        );
    }

    @Test
    void rejectsPatientActiveInConflictingTrial() {
        Patient patient = patient(1);
        Trial trialA = trial(1, "Study A");
        Trial trialB = trial(2, "Study B");

        Enrollment existing = existingEnrollment(
                patient, trialB, EnrollmentStatus.ACTIVE
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> enroll(
                        eligibleScreening(patient, trialA),
                        new Arm(1L, "Arm A", trialA, 50),
                        List.of(existing),
                        Set.of(2L)
                )
        );
    }

    @Test
    void allowsEnrollmentWhenExistingTrialIsNotConflicting() {
        Patient patient = patient(1);
        Trial trialA = trial(1, "Study A");
        Trial trialB = trial(2, "Study B");

        Enrollment existing = existingEnrollment(
                patient, trialB, EnrollmentStatus.ACTIVE
        );

        Enrollment result = enroll(
                eligibleScreening(patient, trialA),
                new Arm(1L, "Arm A", trialA, 50),
                List.of(existing),
                Set.of()
        );

        assertEquals(EnrollmentStatus.ACTIVE, result.status());
    }

    @Test
    void ignoresWithdrawnEnrollmentWhenCheckingConflicts() {
        Patient patient = patient(1);
        Trial trialA = trial(1, "Study A");
        Trial trialB = trial(2, "Study B");

        Enrollment existing = existingEnrollment(
                patient, trialB, EnrollmentStatus.WITHDRAWN
        );

        Enrollment result = enroll(
                eligibleScreening(patient, trialA),
                new Arm(1L, "Arm A", trialA, 50),
                List.of(existing),
                Set.of(2L)
        );

        assertEquals(EnrollmentStatus.ACTIVE, result.status());
    }

    @Test
    void withdrawalPreservesEnrollmentAndRecordsTimestamp() {
        Patient patient = patient(1);
        Trial trial = trial(1, "Study A");
        Enrollment active = enroll(
                eligibleScreening(patient, trial),
                new Arm(1L, "Arm A", trial, 50),
                List.of(), Set.of()
        );
        LocalDateTime withdrawalTime =
                LocalDateTime.of(2026, 1, 12, 10, 0);

        Enrollment withdrawn = service.withdraw(active, withdrawalTime);

        assertEquals(active.id(), withdrawn.id());
        assertEquals(EnrollmentStatus.ACTIVE, active.status());
        assertEquals(EnrollmentStatus.WITHDRAWN, withdrawn.status());
        assertEquals(withdrawalTime, withdrawn.withdrawnAt());
    }

    @Test
    void rejectsSecondWithdrawal() {
        Patient patient = patient(1);
        Trial trial = trial(1, "Study A");
        Enrollment active = enroll(
                eligibleScreening(patient, trial),
                new Arm(1L, "Arm A", trial, 50),
                List.of(), Set.of()
        );

        Enrollment withdrawn = service.withdraw(
                active, active.enrolledAt().plusDays(1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.withdraw(
                        withdrawn, active.enrolledAt().plusDays(2)
                )
        );
    }
}