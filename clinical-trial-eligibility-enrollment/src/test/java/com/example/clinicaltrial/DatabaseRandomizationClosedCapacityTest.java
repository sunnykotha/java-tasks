package com.example.clinicaltrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseRandomizationClosedCapacityTest {

    @TempDir
    Path tempDir;

    private ClinicalTrialDatabase database;
    private Site site;
    private Arm armA;
    private Arm armB;
    private DatabaseRandomizationRepository repository;

    @BeforeEach
    void setUp() {
        String jdbcUrl = "jdbc:h2:file:"
                + tempDir.resolve("closed-capacity").toAbsolutePath();

        database = new ClinicalTrialDatabase(jdbcUrl);

        Trial trial = new Trial(701L, "Closed capacity trial", 20, 10);
        site = new Site(702L, "Site A", trial);
        armA = new Arm(703L, "Arm A", trial, 10);
        armB = new Arm(704L, "Arm B", trial, 10);

        DatabaseCapacityRepository capacityRepository =
                new DatabaseCapacityRepository(database);

        capacityRepository.registerTrial(trial);
        capacityRepository.registerSite(site);
        capacityRepository.registerArm(armA);
        capacityRepository.registerArm(armB);

        repository = new DatabaseRandomizationRepository(database, 4);
    }

    @Test
    void rejectsRandomizationForClosedSite() throws Exception {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE site_capacity SET closed = TRUE WHERE site_id = ?")) {
            statement.setLong(1, site.id());
            assertEquals(1, statement.executeUpdate());
        }

        assertThrows(
                IllegalStateException.class,
                () -> repository.assignNext(site, Sex.FEMALE, armA, armB)
        );

        assertEquals(0, countBlocks());
    }

    @Test
    void rejectsRandomizationWhenEitherArmIsClosed() throws Exception {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE arm_capacity SET closed = TRUE WHERE arm_id = ?")) {
            statement.setLong(1, armB.id());
            assertEquals(1, statement.executeUpdate());
        }

        assertThrows(
                IllegalStateException.class,
                () -> repository.assignNext(site, Sex.FEMALE, armA, armB)
        );

        assertEquals(0, countBlocks());
    }

    private int countBlocks() throws Exception {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM randomization_block");
             var result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        }
    }
}