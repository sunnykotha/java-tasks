package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WithdrawalReplacementRulesTest {

    @Test
    void withdrawalFreesActiveSiteCapacityWhenArmRemainsOpen() {
        Trial trial = new Trial(1L, "Trial", 2, 1);
        Site site = new Site(1L, "Site A", trial);
        Arm arm = new Arm(1L, "Arm A", trial, 2);
        LocalDateTime now = LocalDateTime.of(2026, 10, 27, 10, 0);

        Patient firstPatient = new Patient(
                1L, LocalDate.of(1990, 1, 1), Sex.FEMALE
        );
        Patient secondPatient = new Patient(
                2L, LocalDate.of(1992, 1, 1), Sex.MALE
        );

        Screening firstScreening = new Screening(
                1L, firstPatient, trial, site, now, true, List.of()
        );
        Screening secondScreening = new Screening(
                2L, secondPatient, trial, site, now, true, List.of()
        );

        EnrollmentCapacityService capacity = new EnrollmentCapacityService();
        capacity.enroll(1L, firstScreening, arm, now, Set.of());

        capacity.withdraw(1L, now.plusDays(1));

        Enrollment replacement = capacity.enroll(
                2L, secondScreening, arm, now.plusDays(2), Set.of()
        );

        assertEquals(secondPatient, replacement.patient());
        assertEquals(2, capacity.enrollments().size());
        assertEquals(
                EnrollmentStatus.WITHDRAWN,
                capacity.enrollments().get(0).status()
        );
        assertEquals(
                EnrollmentStatus.ACTIVE,
                capacity.enrollments().get(1).status()
        );
    }

    @Test
    void withdrawalDoesNotReopenArmThatReachedItsCap() {
        Trial trial = new Trial(1L, "Trial", 3, 3);
        Site site = new Site(1L, "Site A", trial);
        Arm arm = new Arm(1L, "Arm A", trial, 1);
        LocalDateTime now = LocalDateTime.of(2026, 10, 27, 10, 0);

        Patient patient = new Patient(
                1L, LocalDate.of(1990, 1, 1), Sex.FEMALE
        );
        Screening screening = new Screening(
                1L, patient, trial, site, now, true, List.of()
        );

        EnrollmentCapacityService capacity = new EnrollmentCapacityService();
        capacity.enroll(1L, screening, arm, now, Set.of());
        capacity.withdraw(1L, now.plusDays(1));

        assertTrue(capacity.isArmClosed(arm.id()));
    }
}