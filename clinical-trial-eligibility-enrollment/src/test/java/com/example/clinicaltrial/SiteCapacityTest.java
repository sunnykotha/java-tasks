package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SiteCapacityTest {

    @Test
    void siteCapPreventsFurtherEnrollmentAtThatSite() {
        Trial trial = new Trial(1L, "Trial", 10, 1);
        Site site = new Site(1L, "Site A", trial);
        Arm arm = new Arm(1L, "Arm A", trial, 10);
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

        assertThrows(
                IllegalStateException.class,
                () -> capacity.enroll(
                        2L, secondScreening, arm, now, Set.of()
                )
        );

        assertEquals(1, capacity.enrollments().size());
    }

    @Test
    void anotherSiteCanEnrollWhileOverallTrialCapacityRemains() {
        Trial trial = new Trial(1L, "Trial", 10, 1);
        Site siteA = new Site(1L, "Site A", trial);
        Site siteB = new Site(2L, "Site B", trial);
        Arm arm = new Arm(1L, "Arm A", trial, 10);
        LocalDateTime now = LocalDateTime.of(2026, 10, 27, 10, 0);

        Patient firstPatient = new Patient(
                1L, LocalDate.of(1990, 1, 1), Sex.FEMALE
        );
        Patient secondPatient = new Patient(
                2L, LocalDate.of(1992, 1, 1), Sex.MALE
        );

        Screening firstScreening = new Screening(
                1L, firstPatient, trial, siteA, now, true, List.of()
        );
        Screening secondScreening = new Screening(
                2L, secondPatient, trial, siteB, now, true, List.of()
        );

        EnrollmentCapacityService capacity = new EnrollmentCapacityService();

        capacity.enroll(1L, firstScreening, arm, now, Set.of());

        Enrollment secondEnrollment = capacity.enroll(
                2L, secondScreening, arm, now, Set.of()
        );

        assertEquals(siteB, secondEnrollment.site());
        assertEquals(2, capacity.enrollments().size());
    }

    @Test
    void withdrawalReopensSiteCapacityForReplacement() {
        Trial trial = new Trial(91L, "Site closure trial", 3, 1);
        Site site = new Site(91L, "Capped site", trial);
        Arm arm = new Arm(91L, "Arm A", trial, 3);
        LocalDateTime now = LocalDateTime.of(2026, 10, 27, 10, 0);
        Patient firstPatient = new Patient(
                91L, LocalDate.of(1990, 1, 1), Sex.FEMALE
        );
        Screening firstScreening = new Screening(
                91L, firstPatient, trial, site, now, true, List.of()
        );

        EnrollmentCapacityService capacity = new EnrollmentCapacityService();
        capacity.enroll(91L, firstScreening, arm, now, Set.of());

        assertTrue(capacity.isSiteClosed(site.id()));

        capacity.withdraw(91L, now.plusDays(1));

        assertFalse(capacity.isSiteClosed(site.id()));

        Patient replacementPatient = new Patient(
                92L, LocalDate.of(1991, 1, 1), Sex.MALE
        );
        Screening replacementScreening = new Screening(
                92L, replacementPatient, trial, site,
                now.plusDays(2), true, List.of()
        );

        capacity.enroll(
                92L, replacementScreening, arm, now.plusDays(2), Set.of()
        );

        assertTrue(capacity.isSiteClosed(site.id()));
    }
}