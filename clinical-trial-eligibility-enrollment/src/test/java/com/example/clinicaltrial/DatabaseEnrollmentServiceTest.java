package com.example.clinicaltrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseEnrollmentServiceTest {

    @TempDir
    Path tempDir;

    private record Fixture(
            ClinicalTrialDatabase database,
            DatabaseEnrollmentService service,
            EnrollmentRepository enrollments,
            Trial trial,
            Site site,
            Arm arm
    ) {}

    private Fixture fixture(
            String databaseName,
            int trialCap,
            int siteCap,
            int armCap
    ) {
        String databasePath = tempDir.resolve(databaseName)
                .toAbsolutePath().toString().replace("\\", "/");

        ClinicalTrialDatabase database = new ClinicalTrialDatabase(
                "jdbc:h2:file:" + databasePath
        );

        Trial trial = new Trial(101L, "Trial A", trialCap, siteCap);
        Site site = new Site(201L, "Site A", trial);
        Arm arm = new Arm(301L, "Arm A", trial, armCap);

        DatabaseCapacityRepository capacities =
                new DatabaseCapacityRepository(database);
        capacities.registerTrial(trial);
        capacities.registerSite(site);
        capacities.registerArm(arm);

        return new Fixture(
                database,
                new DatabaseEnrollmentService(database),
                new EnrollmentRepository(database),
                trial,
                site,
                arm
        );
    }

    private Patient patient(long id) {
        return new Patient(
                id, LocalDate.of(1985, 5, 10), Sex.FEMALE
        );
    }

    private Screening eligibleScreening(
            Fixture fixture, long patientId
    ) {
        return new Screening(
                500L + patientId,
                patient(patientId),
                fixture.trial(),
                fixture.site(),
                LocalDateTime.of(2026, 10, 27, 10, 0),
                true,
                List.of()
        );
    }

    private Enrollment seedEnrollment(
            Fixture fixture,
            long enrollmentId,
            long patientId
    ) {
        Enrollment enrollment = new Enrollment(
                enrollmentId,
                patient(patientId),
                fixture.trial(),
                fixture.site(),
                fixture.arm(),
                LocalDateTime.of(2026, 10, 27, 9, 0),
                EnrollmentStatus.ACTIVE,
                null
        );
        fixture.enrollments().save(enrollment);
        return enrollment;
    }

    @Test
    void persistsSuccessfulEnrollment() {
        Fixture fixture = fixture("success-db", 3, 3, 3);

        Enrollment enrollment = fixture.service().enroll(
                1001L,
                eligibleScreening(fixture, 1L),
                fixture.arm(),
                LocalDateTime.of(2026, 10, 27, 10, 0),
                Set.of()
        );

        assertEquals(1001L, enrollment.id());
        assertTrue(fixture.enrollments().exists(1001L));
    }

    @Test
    void rejectsEnrollmentWhenOverallTrialCapIsReached() {
        Fixture fixture = fixture("trial-cap-db", 1, 5, 5);
        seedEnrollment(fixture, 1001L, 1L);

        assertThrows(IllegalStateException.class, () ->
                fixture.service().enroll(
                        1002L,
                        eligibleScreening(fixture, 2L),
                        fixture.arm(),
                        LocalDateTime.of(2026, 10, 27, 10, 0),
                        Set.of()
                )
        );

        assertFalse(fixture.enrollments().exists(1002L));
    }

    @Test
    void rejectsEnrollmentWhenSiteCapIsReached() {
        Fixture fixture = fixture("site-cap-db", 5, 1, 5);
        seedEnrollment(fixture, 1001L, 1L);

        assertThrows(IllegalStateException.class, () ->
                fixture.service().enroll(
                        1002L,
                        eligibleScreening(fixture, 2L),
                        fixture.arm(),
                        LocalDateTime.of(2026, 10, 27, 10, 0),
                        Set.of()
                )
        );

        assertFalse(fixture.enrollments().exists(1002L));
    }

    @Test
    void rejectsEnrollmentWhenArmCapIsReached() {
        Fixture fixture = fixture("arm-cap-db", 5, 5, 1);
        seedEnrollment(fixture, 1001L, 1L);

        assertThrows(IllegalStateException.class, () ->
                fixture.service().enroll(
                        1002L,
                        eligibleScreening(fixture, 2L),
                        fixture.arm(),
                        LocalDateTime.of(2026, 10, 27, 10, 0),
                        Set.of()
                )
        );

        assertFalse(fixture.enrollments().exists(1002L));
    }
}