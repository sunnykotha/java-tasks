package com.example.clinicaltrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseRandomizationConcurrencyTest {

    @TempDir
    Path tempDir;

    @Test
    void concurrentRequestsConsumeEachBlockSlotOnlyOnce() throws Exception {
        String jdbcUrl = "jdbc:h2:file:"
                + tempDir.resolve("randomization-concurrency").toAbsolutePath();

        ClinicalTrialDatabase database =
                new ClinicalTrialDatabase(jdbcUrl);

        Trial trial = new Trial(401L, "Concurrent trial", 20, 10);
        Site site = new Site(501L, "Site A", trial);
        Arm armA = new Arm(601L, "Arm A", trial, 10);
        Arm armB = new Arm(602L, "Arm B", trial, 10);

        DatabaseCapacityRepository capacityRepository =
                new DatabaseCapacityRepository(database);

        capacityRepository.registerTrial(trial);
        capacityRepository.registerSite(site);
        capacityRepository.registerArm(armA);
        capacityRepository.registerArm(armB);

        DatabaseRandomizationRepository repositoryA =
                new DatabaseRandomizationRepository(database, 4);

        ClinicalTrialDatabase secondDatabase =
                new ClinicalTrialDatabase(jdbcUrl);

        DatabaseRandomizationRepository repositoryB =
                new DatabaseRandomizationRepository(secondDatabase, 4);

        int requestCount = 4;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);

        try {
            List<Future<Arm>> futures = new ArrayList<>();

            for (int i = 0; i < requestCount; i++) {
                final int requestNumber = i;

                futures.add(executor.submit(() -> {
                    ready.countDown();

                    if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Timed out waiting for concurrent start");
                    }

                    DatabaseRandomizationRepository repository =
                            requestNumber % 2 == 0 ? repositoryA : repositoryB;

                    return repository.assignNext(site, Sex.FEMALE, armA, armB);
                }));
            }

            ready.await();
            start.countDown();

            int armACount = 0;
            int armBCount = 0;

            for (Future<Arm> future : futures) {
                Arm assignedArm = future.get();

                assertNotNull(assignedArm);

                if (assignedArm.id().equals(armA.id())) {
                    armACount++;
                } else if (assignedArm.id().equals(armB.id())) {
                    armBCount++;
                } else {
                    throw new AssertionError(
                            "Unexpected randomized arm: " + assignedArm.id());
                }
            }

            assertEquals(2, armACount);
            assertEquals(2, armBCount);

            try (Connection connection = database.getConnection()) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM randomization_assignment "
                                + "WHERE consumed_at IS NOT NULL")) {
                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        assertEquals(4, result.getInt(1));
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT next_slot FROM randomization_block "
                                + "WHERE site_id = ? AND sex = ?")) {
                    statement.setLong(1, site.id());
                    statement.setString(2, Sex.FEMALE.name());

                    try (ResultSet result = statement.executeQuery()) {
                        assertEquals(true, result.next());
                        assertEquals(4, result.getInt("next_slot"));
                        assertEquals(false, result.next());
                    }
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }
}