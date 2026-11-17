package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClinicalTrialDatabaseTest {

    @Test
    void initializesRequiredTables() throws Exception {
        ClinicalTrialDatabase database = new ClinicalTrialDatabase(
            "jdbc:h2:mem:clinical_trial_schema;DB_CLOSE_DELAY=-1"
        );

        Set<String> tableNames = new HashSet<>();

        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                 SELECT TABLE_NAME
                 FROM INFORMATION_SCHEMA.TABLES
                 WHERE TABLE_SCHEMA = 'PUBLIC'
                 """)) {

            while (result.next()) {
                tableNames.add(result.getString("TABLE_NAME"));
            }
        }

        assertEquals(
            Set.of(
                "TRIAL_CAPACITY",
                "SITE_CAPACITY",
                "ARM_CAPACITY",
                "TRIAL_ENROLLMENT",
                "RANDOMIZATION_BLOCK",
                "RANDOMIZATION_ASSIGNMENT"
            ),
            tableNames
        );
    }

    @Test
    void rejectsBlankJdbcUrl() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ClinicalTrialDatabase(" ")
        );
    }

    @Test
    void retainsEnrollmentDataAcrossConnections() throws Exception {
        java.nio.file.Path databaseDirectory =
            java.nio.file.Files.createTempDirectory("clinical-trial-db-");
        String databasePath = databaseDirectory.resolve("persistence").toString();
        String jdbcUrl = "jdbc:h2:file:" + databasePath;

        try {
            ClinicalTrialDatabase database = new ClinicalTrialDatabase(jdbcUrl);

            try (Connection connection = database.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                    INSERT INTO trial_enrollment
                        (enrollment_id, patient_id, trial_id, site_id, arm_id,
                         enrolled_at, status)
                    VALUES
                        (9001, 101, 201, 301, 401,
                         TIMESTAMP '2026-10-27 10:00:00', 'ACTIVE')
                    """);
            }

            try (Connection connection = database.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("""
                     SELECT patient_id, status
                     FROM trial_enrollment
                     WHERE enrollment_id = 9001
                     """)) {
                org.junit.jupiter.api.Assertions.assertTrue(result.next());
                assertEquals(101L, result.getLong("patient_id"));
                assertEquals("ACTIVE", result.getString("status"));
            }
        } finally {
            try (var stream = java.nio.file.Files.walk(databaseDirectory)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(file -> {
                        try {
                            java.nio.file.Files.deleteIfExists(file);
                        } catch (java.io.IOException exception) {
                            throw new java.io.UncheckedIOException(exception);
                        }
                    });
            }
        }
    }
}