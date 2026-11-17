package com.example.clinicaltrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseEnrollmentConcurrencyTest {

    @TempDir
    Path tempDir;

    @Test
    void onlyOneConcurrentAttemptCanClaimTheLastTrialSlot() throws Exception {
        String databasePath = tempDir.resolve("concurrent-enrollment-db")
                .toAbsolutePath().toString().replace("\\", "/");

        String jdbcUrl = "jdbc:h2:file:" + databasePath;
        ClinicalTrialDatabase database = new ClinicalTrialDatabase(jdbcUrl);

        Trial trial = new Trial(101L, "Concurrent Trial", 1, 1);
        Site site = new Site(201L, "Concurrent Site", trial);
        Arm arm = new Arm(301L, "Concurrent Arm", trial, 1);

        DatabaseCapacityRepository capacities =
                new DatabaseCapacityRepository(database);
        capacities.registerTrial(trial);
        capacities.registerSite(site);
        capacities.registerArm(arm);

        DatabaseEnrollmentService serviceOne =
                new DatabaseEnrollmentService(database);
        DatabaseEnrollmentService serviceTwo =
                new DatabaseEnrollmentService(
                        new ClinicalTrialDatabase(jdbcUrl)
                );
        EnrollmentRepository repository = new EnrollmentRepository(database);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> attemptEnrollment(
                    serviceOne, trial, site, arm, 1001L, 1L,
                    ready, start, succeeded, rejected
            ));

            Future<?> second = executor.submit(() -> attemptEnrollment(
                    serviceTwo, trial, site, arm, 1002L, 2L,
                    ready, start, succeeded, rejected
            ));

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);

            assertEquals(1, succeeded.get());
            assertEquals(1, rejected.get());

            int persisted = 0;
            if (repository.exists(1001L)) {
                persisted++;
            }
            if (repository.exists(1002L)) {
                persisted++;
            }
            assertEquals(1, persisted);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private void attemptEnrollment(
            DatabaseEnrollmentService service,
            Trial trial,
            Site site,
            Arm arm,
            long enrollmentId,
            long patientId,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger succeeded,
            AtomicInteger rejected
    ) {
        ready.countDown();

        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start");
            }

            Patient patient = new Patient(
                    patientId, LocalDate.of(1985, 5, 10), Sex.FEMALE
            );

            Screening screening = new Screening(
                    500L + patientId,
                    patient,
                    trial,
                    site,
                    LocalDateTime.of(2026, 10, 27, 10, 0),
                    true,
                    List.of()
            );

            service.enroll(
                    enrollmentId,
                    screening,
                    arm,
                    LocalDateTime.of(2026, 10, 27, 10, 0),
                    Set.of()
            );
            succeeded.incrementAndGet();
        } catch (IllegalStateException exception) {
            rejected.incrementAndGet();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Enrollment attempt was interrupted", exception
            );
        }
    }
}