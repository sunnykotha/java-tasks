package com.example.clinicaltrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseWithdrawalTest {

    @TempDir
    Path tempDir;

    @Test
    void withdrawalIsPersistedAndAllowsReplacementEnrollment() throws Exception {
        String dbPath = tempDir.resolve("withdrawal-db")
                .toAbsolutePath().toString().replace("\\", "/");
        ClinicalTrialDatabase database =
                new ClinicalTrialDatabase("jdbc:h2:file:" + dbPath);

        Trial trial = new Trial(101L, "Withdrawal Trial", 5, 1);
        Site site = new Site(201L, "Withdrawal Site", trial);
        Arm arm = new Arm(301L, "Withdrawal Arm", trial, 1);

        DatabaseCapacityRepository capacityRepository =
                new DatabaseCapacityRepository(database);
        capacityRepository.registerTrial(trial);
        capacityRepository.registerSite(site);
        capacityRepository.registerArm(arm);

        DatabaseEnrollmentService service =
                new DatabaseEnrollmentService(database);
        EnrollmentRepository enrollmentRepository =
                new EnrollmentRepository(database);

        LocalDateTime enrollmentTime =
                LocalDateTime.of(2026, 10, 27, 10, 0);

        Patient firstPatient = patient(1L);
        Screening firstScreening = screening(
                501L, firstPatient, trial, site, enrollmentTime
        );

        service.enroll(
                1001L, firstScreening, arm, enrollmentTime, Set.of()
        );

        service.withdraw(
                1001L, enrollmentTime.plusHours(2)
        );

        assertTrue(enrollmentRepository.exists(1001L));

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT status, withdrawn_at
                     FROM trial_enrollment WHERE enrollment_id = ?
                     """)) {
            statement.setLong(1, 1001L);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals("WITHDRAWN", result.getString("status"));
                assertEquals(
                        enrollmentTime.plusHours(2),
                        result.getTimestamp("withdrawn_at").toLocalDateTime()
                );
            }
        }

        assertFalse(isClosed(database, "site_capacity", "site_id", 201L));
        assertFalse(isClosed(database, "arm_capacity", "arm_id", 301L));

        Patient replacementPatient = patient(2L);
        Screening replacementScreening = screening(
                502L, replacementPatient, trial, site,
                enrollmentTime.plusDays(1)
        );

        Enrollment replacement = service.enroll(
                1002L,
                replacementScreening,
                arm,
                enrollmentTime.plusDays(1),
                Set.of()
        );

        assertEquals(1002L, replacement.id());
        assertTrue(enrollmentRepository.exists(1002L));
    }

    private Patient patient(long id) {
        return new Patient(
                id, LocalDate.of(1985, 5, 10), Sex.FEMALE
        );
    }

    private Screening screening(
            long screeningId,
            Patient patient,
            Trial trial,
            Site site,
            LocalDateTime time
    ) {
        return new Screening(
                screeningId, patient, trial, site, time, true, List.of()
        );
    }

    private boolean isClosed(
            ClinicalTrialDatabase database,
            String table,
            String idColumn,
            long id
    ) throws Exception {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT closed FROM " + table + " WHERE "
                             + idColumn + " = ?"
             )) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getBoolean("closed");
            }
        }
    }
}