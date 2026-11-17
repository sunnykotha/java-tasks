package com.example.clinicaltrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

class DatabaseRandomizedEnrollmentTest {

    @TempDir
    Path tempDir;

    private record Fixture(
            ClinicalTrialDatabase database,
            DatabaseEnrollmentService enrollmentService,
            DatabaseRandomizationRepository randomizationRepository,
            EnrollmentRepository enrollmentRepository,
            Trial trial,
            Site site,
            Arm armA,
            Arm armB
    ) {}

    private Fixture fixture(String databaseName) {
        String databasePath = tempDir.resolve(databaseName)
                .toAbsolutePath().toString().replace("\\", "/");

        ClinicalTrialDatabase database = new ClinicalTrialDatabase(
                "jdbc:h2:file:" + databasePath
        );

        Trial trial = new Trial(701L, "Randomized Trial", 10, 10);
        Site site = new Site(702L, "Site A", trial);
        Arm armA = new Arm(703L, "Arm A", trial, 10);
        Arm armB = new Arm(704L, "Arm B", trial, 10);

        DatabaseCapacityRepository capacities =
                new DatabaseCapacityRepository(database);
        capacities.registerTrial(trial);
        capacities.registerSite(site);
        capacities.registerArm(armA);
        capacities.registerArm(armB);

        return new Fixture(
                database,
                new DatabaseEnrollmentService(database),
                new DatabaseRandomizationRepository(database, 4),
                new EnrollmentRepository(database),
                trial,
                site,
                armA,
                armB
        );
    }

    private Patient patient(long id) {
        return new Patient(
                id,
                LocalDate.of(1985, 5, 10),
                Sex.FEMALE
        );
    }

    private Screening eligibleScreening(Fixture fixture, long patientId) {
        return new Screening(
                800L + patientId,
                patient(patientId),
                fixture.trial(),
                fixture.site(),
                LocalDateTime.of(2026, 10, 27, 10, 0),
                true,
                List.of()
        );
    }

    private Enrollment enrollRandomized(
            Fixture fixture,
            long enrollmentId,
            long patientId
    ) {
        return fixture.enrollmentService().enrollRandomized(
                enrollmentId,
                eligibleScreening(fixture, patientId),
                Sex.FEMALE,
                fixture.armA(),
                fixture.armB(),
                LocalDateTime.of(2026, 10, 27, 10, 0),
                Set.of(),
                fixture.randomizationRepository()
        );
    }

    @Test
    void persistsEnrollmentAndLinksConsumedRandomizationAssignment()
            throws Exception {
        Fixture fixture = fixture("randomized-success");

        Enrollment enrollment = enrollRandomized(fixture, 9001L, 1L);

        assertTrue(fixture.enrollmentRepository().exists(9001L));

        try (Connection connection = fixture.database().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT arm_id, consumed_at
                     FROM randomization_assignment
                     WHERE consumed_by_enrollment_id = ?
                     """)) {
            statement.setLong(1, enrollment.id());

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(
                        enrollment.arm().id(),
                        result.getLong("arm_id")
                );
                assertTrue(result.getTimestamp("consumed_at") != null);
                assertFalse(result.next());
            }
        }
    }

    @Test
    void rollsBackRandomizationWhenSelectedArmIsAtCapacity()
            throws Exception {
        Fixture fixture = fixture("randomized-arm-capacity");

        try (Connection connection = fixture.database().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE arm_capacity
                     SET max_enrollment = 1
                     WHERE arm_id = ?
                     """)) {
            statement.setLong(1, fixture.armA().id());
            assertEquals(1, statement.executeUpdate());
        }

        Enrollment existing = new Enrollment(
                9009L,
                new Patient(9L, java.time.LocalDate.of(1980, 1, 1), Sex.FEMALE),
                fixture.trial(),
                fixture.site(),
                fixture.armA(),
                java.time.LocalDateTime.of(2026, 1, 1, 9, 0),
                EnrollmentStatus.ACTIVE,
                null
        );
        fixture.enrollmentRepository().save(existing);

        // Get a successful assignment first, ensuring the randomization block
        // exists and its next slot can be checked after the failed attempt.
        boolean successful = false;
        for (long attempt = 0; attempt < 50 && !successful; attempt++) {
            try {
                enrollRandomized(fixture, 9100L + attempt, 100L + attempt);
                successful = true;
            } catch (IllegalStateException ignored) {
                // An assignment to the full arm must fail.
            }
        }
        assertTrue(successful, "Expected to obtain a successful assignment to the other arm");

        long blockId;
        int nextSlot;
        try (Connection connection = fixture.database().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT block_id, next_slot
                     FROM randomization_block
                     WHERE site_id = ? AND sex = ?
                     """)) {
            statement.setLong(1, fixture.site().id());
            statement.setString(2, Sex.FEMALE.name());

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                blockId = result.getLong("block_id");
                nextSlot = result.getInt("next_slot");
            }
        }

        // Force the next persisted slot to the full arm so the test is
        // deterministic and exercises the capacity-failure rollback path.
        try (Connection connection = fixture.database().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE randomization_assignment
                     SET arm_id = ?
                     WHERE block_id = ? AND slot_index = ?
                     """)) {
            statement.setLong(1, fixture.armA().id());
            statement.setLong(2, blockId);
            statement.setInt(3, nextSlot);
            assertEquals(1, statement.executeUpdate());
        }

        assertThrows(
                IllegalStateException.class,
                () -> enrollRandomized(fixture, 9200L, 200L)
        );

        assertFalse(fixture.enrollmentRepository().exists(9200L));

        try (Connection connection = fixture.database().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT next_slot
                     FROM randomization_block
                     WHERE block_id = ?
                     """)) {
            statement.setLong(1, blockId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(nextSlot, result.getInt(1),
                        "Failed enrollment must not advance the randomization block");
            }
        }

        try (Connection connection = fixture.database().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM randomization_assignment
                     WHERE consumed_by_enrollment_id = ?
                     """)) {
            statement.setLong(1, 9200L);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(0L, result.getLong(1),
                        "Failed enrollment must not consume an assignment");
            }
        }
    }
    @Test
    void withdrawalPreservesRandomizationAndReplacementGetsNewAssignment()
            throws Exception {
        Fixture fixture = fixture("randomized-withdrawal-replacement");

        Enrollment original = enrollRandomized(fixture, 9301L, 301L);

        LocalDateTime withdrawnAt =
                LocalDateTime.of(2026, 10, 27, 12, 0);

        fixture.enrollmentService().withdraw(9301L, withdrawnAt);

        long originalBlockId;
        int originalSlot;

        try (Connection connection = fixture.database().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT block_id, slot_index, consumed_at
                     FROM randomization_assignment
                     WHERE consumed_by_enrollment_id = ?
                     """)) {
            statement.setLong(1, original.id());

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(),
                        "Withdrawal must preserve the original assignment");

                originalBlockId = result.getLong("block_id");
                originalSlot = result.getInt("slot_index");
                assertTrue(result.getTimestamp("consumed_at") != null,
                        "Original assignment must retain its consumption timestamp");
                assertFalse(result.next(),
                        "Original enrollment must have exactly one assignment");
            }
        }

        Enrollment replacement = enrollRandomized(fixture, 9302L, 302L);

        assertTrue(fixture.enrollmentRepository().exists(9302L));

        try (Connection connection = fixture.database().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT block_id, slot_index, consumed_at
                     FROM randomization_assignment
                     WHERE consumed_by_enrollment_id = ?
                     """)) {
            statement.setLong(1, replacement.id());

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(),
                        "Replacement must receive a randomization assignment");

                long replacementBlockId = result.getLong("block_id");
                int replacementSlot = result.getInt("slot_index");

                assertTrue(result.getTimestamp("consumed_at") != null);
                assertTrue(
                        originalBlockId != replacementBlockId
                                || originalSlot != replacementSlot,
                        "Replacement must use a different randomization slot"
                );
                assertFalse(result.next(),
                        "Replacement must have exactly one assignment");
            }
        }

        try (Connection connection = fixture.database().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT status, withdrawn_at
                     FROM trial_enrollment
                     WHERE enrollment_id = ?
                     """)) {
            statement.setLong(1, original.id());

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(
                        EnrollmentStatus.WITHDRAWN.name(),
                        result.getString("status")
                );
                assertTrue(result.getTimestamp("withdrawn_at") != null);
            }
        }
    }
    @Test
    void rollsBackRandomizationBlockWhenEnrollmentPersistenceFails()
            throws Exception {
        Fixture fixture = fixture("randomized-rollback");

        Enrollment existing = new Enrollment(
                9002L,
                patient(2L),
                fixture.trial(),
                fixture.site(),
                fixture.armA(),
                LocalDateTime.of(2026, 10, 27, 9, 0),
                EnrollmentStatus.ACTIVE,
                null
        );
        fixture.enrollmentRepository().save(existing);

        assertThrows(
                IllegalStateException.class,
                () -> enrollRandomized(fixture, 9002L, 3L)
        );

        assertTrue(fixture.enrollmentRepository().exists(9002L));

        try (Connection connection = fixture.database().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM randomization_block"
             );
             ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            assertEquals(0L, result.getLong(1));
        }

        try (Connection connection = fixture.database().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM randomization_assignment
                     WHERE consumed_by_enrollment_id = ?
                     """)) {
            statement.setLong(1, 9002L);

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(0L, result.getLong(1));
            }
        }
    }
}