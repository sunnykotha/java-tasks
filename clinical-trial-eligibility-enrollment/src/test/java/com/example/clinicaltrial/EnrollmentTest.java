package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class EnrollmentTest {

    private Patient patient() {
        return new Patient(1L, LocalDate.of(1990, 5, 15), Sex.FEMALE);
    }

    private Trial trial() {
        return new Trial(1L, "Study A", 100, 25);
    }

    private Enrollment activeEnrollment() {
        Trial trial = trial();
        return new Enrollment(
                1L,
                patient(),
                trial,
                new Site(1L, "Site A", trial),
                new Arm(1L, "Arm A", trial, 50),
                LocalDateTime.of(2026, 1, 10, 10, 0),
                EnrollmentStatus.ACTIVE,
                null
        );
    }

    @Test
    void createsActiveEnrollment() {
        Enrollment enrollment = activeEnrollment();

        assertEquals(EnrollmentStatus.ACTIVE, enrollment.status());
        assertNull(enrollment.withdrawnAt());
    }

    @Test
    void rejectsNonPositiveId() {
        Enrollment enrollment = activeEnrollment();

        assertThrows(
                IllegalArgumentException.class,
                () -> new Enrollment(
                        0L, enrollment.patient(), enrollment.trial(),
                        enrollment.site(), enrollment.arm(),
                        enrollment.enrolledAt(), EnrollmentStatus.ACTIVE, null
                )
        );
    }

    @Test
    void rejectsArmFromAnotherTrial() {
        Enrollment enrollment = activeEnrollment();
        Trial otherTrial = new Trial(2L, "Study B", 50, 10);

        assertThrows(
                IllegalArgumentException.class,
                () -> new Enrollment(
                        2L, enrollment.patient(), enrollment.trial(),
                        enrollment.site(),
                        new Arm(2L, "Arm B", otherTrial, 20),
                        enrollment.enrolledAt(), EnrollmentStatus.ACTIVE, null
                )
        );
    }

    @Test
    void withdrawnEnrollmentRequiresTimestamp() {
        Enrollment enrollment = activeEnrollment();

        assertThrows(
                IllegalArgumentException.class,
                () -> new Enrollment(
                        enrollment.id(), enrollment.patient(),
                        enrollment.trial(), enrollment.site(),
                        enrollment.arm(), enrollment.enrolledAt(),
                        EnrollmentStatus.WITHDRAWN, null
                )
        );
    }

    @Test
    void rejectsWithdrawalBeforeEnrollment() {
        Enrollment enrollment = activeEnrollment();

        assertThrows(
                IllegalArgumentException.class,
                () -> new Enrollment(
                        enrollment.id(), enrollment.patient(),
                        enrollment.trial(), enrollment.site(),
                        enrollment.arm(), enrollment.enrolledAt(),
                        EnrollmentStatus.WITHDRAWN,
                        enrollment.enrolledAt().minusMinutes(1)
                )
        );
    }
}