package com.example.clinicaltrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseRandomizationRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsAndConsumesBalancedBlockAcrossRepositoryInstances()
            throws Exception {
        String path = tempDir.resolve("randomization-db")
                .toAbsolutePath().toString().replace("\\", "/");
        String jdbcUrl = "jdbc:h2:file:" + path;

        ClinicalTrialDatabase database = new ClinicalTrialDatabase(jdbcUrl);
        Trial trial = new Trial(101L, "Randomization Trial", 20, 10);
        Site site = new Site(201L, "Randomization Site", trial);
        Arm armA = new Arm(301L, "Arm A", trial, 10);
        Arm armB = new Arm(302L, "Arm B", trial, 10);

        DatabaseCapacityRepository capacities =
                new DatabaseCapacityRepository(database);
        capacities.registerTrial(trial);
        capacities.registerSite(site);
        capacities.registerArm(armA);
        capacities.registerArm(armB);

        DatabaseRandomizationRepository first =
                new DatabaseRandomizationRepository(database, 4);

        Map<Long, Integer> assignedCounts = new HashMap<>();
        for (int i = 0; i < 2; i++) {
            Arm assigned = first.assignNext(site, Sex.FEMALE, armA, armB);
            assignedCounts.merge(assigned.id(), 1, Integer::sum);
        }

        DatabaseRandomizationRepository restarted =
                new DatabaseRandomizationRepository(
                        new ClinicalTrialDatabase(jdbcUrl), 4
                );

        for (int i = 0; i < 2; i++) {
            Arm assigned = restarted.assignNext(site, Sex.FEMALE, armA, armB);
            assignedCounts.merge(assigned.id(), 1, Integer::sum);
        }

        assertEquals(2, assignedCounts.getOrDefault(armA.id(), 0));
        assertEquals(2, assignedCounts.getOrDefault(armB.id(), 0));

        try (Connection connection = database.getConnection();
             PreparedStatement blockStatement = connection.prepareStatement("""
                     SELECT block_id, next_slot, block_size
                     FROM randomization_block WHERE site_id = ? AND sex = ?
                     """)) {
            blockStatement.setLong(1, site.id());
            blockStatement.setString(2, Sex.FEMALE.name());

            try (ResultSet blocks = blockStatement.executeQuery()) {
                assertTrue(blocks.next());
                long blockId = blocks.getLong("block_id");
                assertEquals(4, blocks.getInt("next_slot"));
                assertEquals(4, blocks.getInt("block_size"));
                assertTrue(!blocks.next());

                try (PreparedStatement assignments = connection.prepareStatement("""
                        SELECT COUNT(*) AS total,
                               COUNT(consumed_at) AS consumed,
                               SUM(CASE WHEN arm_id = ? THEN 1 ELSE 0 END) AS arm_a_count,
                               SUM(CASE WHEN arm_id = ? THEN 1 ELSE 0 END) AS arm_b_count
                        FROM randomization_assignment
                        WHERE block_id = ?
                        """)) {
                    assignments.setLong(1, armA.id());
                    assignments.setLong(2, armB.id());
                    assignments.setLong(3, blockId);

                    try (ResultSet result = assignments.executeQuery()) {
                        assertTrue(result.next());
                        assertEquals(4, result.getInt("total"));
                        assertEquals(4, result.getInt("consumed"));
                        assertEquals(2, result.getInt("arm_a_count"));
                        assertEquals(2, result.getInt("arm_b_count"));
                    }
                }
            }
        }
    }

    @Test
    void createsSeparateBlocksForDifferentStrata() throws Exception {
        String path = tempDir.resolve("strata-db")
                .toAbsolutePath().toString().replace("\\", "/");
        ClinicalTrialDatabase database =
                new ClinicalTrialDatabase("jdbc:h2:file:" + path);

        Trial trial = new Trial(111L, "Strata Trial", 20, 10);
        Site siteA = new Site(211L, "Site A", trial);
        Site siteB = new Site(212L, "Site B", trial);
        Arm armA = new Arm(311L, "Arm A", trial, 10);
        Arm armB = new Arm(312L, "Arm B", trial, 10);

        DatabaseCapacityRepository capacities =
                new DatabaseCapacityRepository(database);
        capacities.registerTrial(trial);
        capacities.registerSite(siteA);
        capacities.registerSite(siteB);
        capacities.registerArm(armA);
        capacities.registerArm(armB);

        DatabaseRandomizationRepository repository =
                new DatabaseRandomizationRepository(database, 4);

        assertNotNull(repository.assignNext(siteA, Sex.FEMALE, armA, armB));
        assertNotNull(repository.assignNext(siteA, Sex.MALE, armA, armB));
        assertNotNull(repository.assignNext(siteB, Sex.FEMALE, armA, armB));

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM randomization_block"
             );
             ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            assertEquals(3, result.getInt(1));
        }
    }
}