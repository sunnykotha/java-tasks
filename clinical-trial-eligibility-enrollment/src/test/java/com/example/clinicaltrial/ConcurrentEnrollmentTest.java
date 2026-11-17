package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentEnrollmentTest {

    @Test
    void onlyOneCoordinatorCanClaimTheLastOverallSlot() throws Exception {
        Trial trial = new Trial(1L, "Trial", 1, 1);
        Site site = new Site(1L, "Site A", trial);
        Arm arm = new Arm(1L, "Arm A", trial, 2);

        Patient patientA = new Patient(
                1L, LocalDate.of(1990, 1, 1), Sex.FEMALE
        );
        Patient patientB = new Patient(
                2L, LocalDate.of(1992, 1, 1), Sex.MALE
        );

        LocalDateTime now = LocalDateTime.of(2026, 10, 27, 10, 0);

        Screening screeningA = new Screening(
                1L, patientA, trial, site, now, true, List.of()
        );
        Screening screeningB = new Screening(
                2L, patientB, trial, site, now, true, List.of()
        );

        EnrollmentCapacityService capacity = new EnrollmentCapacityService();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    capacity.enroll(1L, screeningA, arm, now, Set.of());
                    return true;
                } catch (IllegalStateException exception) {
                    return false;
                }
            });

            Future<Boolean> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    capacity.enroll(2L, screeningB, arm, now, Set.of());
                    return true;
                } catch (IllegalStateException exception) {
                    return false;
                }
            });

            ready.await();
            start.countDown();

            int successfulEnrollments =
                    (first.get() ? 1 : 0) + (second.get() ? 1 : 0);

            assertEquals(1, successfulEnrollments);
            assertEquals(1, capacity.enrollments().size());
        } finally {
            executor.shutdownNow();
        }
    }
}