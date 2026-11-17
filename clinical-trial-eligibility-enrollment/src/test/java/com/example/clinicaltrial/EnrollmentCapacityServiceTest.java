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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EnrollmentCapacityServiceTest {

    private Patient patient(long id) {
        return new Patient(
                id, LocalDate.of(1990, 5, 15), Sex.FEMALE
        );
    }

    private Trial trial(long id, int totalCap, int siteCap) {
        return new Trial(id, "Study " + id, totalCap, siteCap);
    }

    private Site site(long id, Trial trial) {
        return new Site(id, "Site " + id, trial);
    }

    private Arm arm(long id, Trial trial, int cap) {
        return new Arm(id, "Arm " + id, trial, cap);
    }

    private Screening screening(
            long screeningId, Patient patient, Site site
    ) {
        return new Screening(
                screeningId,
                patient,
                site.trial(),
                site,
                LocalDateTime.of(2026, 1, 10, 9, 0),
                true,
                List.of()
        );
    }

    private LocalDateTime enrollmentTime(int minute) {
        return LocalDateTime.of(2026, 1, 10, 10, minute);
    }

    private EnrollmentCapacityService newService() {
        return new EnrollmentCapacityService();
    }

    @Test
    void acceptsEnrollmentBelowAllCaps() {
        Trial trial = trial(1, 10, 5);
        Site site = site(1, trial);
        Arm arm = arm(1, trial, 5);
        EnrollmentCapacityService service = newService();

        Enrollment result = service.enroll(
                1L, screening(1, patient(1), site),
                arm, enrollmentTime(0), Set.of()
        );

        assertEquals(EnrollmentStatus.ACTIVE, result.status());
        assertEquals(1, service.enrollments().size());
    }

    @Test
    void rejectsEnrollmentWhenOverallCapIsReached() {
        Trial trial = trial(1, 1, 1);
        Site site = site(1, trial);
        Arm arm = arm(1, trial, 1);
        EnrollmentCapacityService service = newService();

        service.enroll(
                1L, screening(1, patient(1), site),
                arm, enrollmentTime(0), Set.of()
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.enroll(
                        2L, screening(2, patient(2), site),
                        arm, enrollmentTime(1), Set.of()
                )
        );
    }

    @Test
    void rejectsEnrollmentWhenSiteCapIsReached() {
        Trial trial = trial(1, 10, 1);
        Site site = site(1, trial);
        EnrollmentCapacityService service = newService();

        service.enroll(
                1L, screening(1, patient(1), site),
                arm(1, trial, 5), enrollmentTime(0), Set.of()
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.enroll(
                        2L, screening(2, patient(2), site),
                        arm(2, trial, 5), enrollmentTime(1), Set.of()
                )
        );
    }

    @Test
    void rejectsEnrollmentWhenArmCapIsReached() {
        Trial trial = trial(1, 10, 10);
        Site site = site(1, trial);
        Arm arm = arm(1, trial, 1);
        EnrollmentCapacityService service = newService();

        service.enroll(
                1L, screening(1, patient(1), site),
                arm, enrollmentTime(0), Set.of()
        );

        assertTrue(service.isArmClosed(arm.id()));

        assertThrows(
                IllegalStateException.class,
                () -> service.enroll(
                        2L, screening(2, patient(2), site),
                        arm, enrollmentTime(1), Set.of()
                )
        );
    }

    @Test
    void rejectsEnrollmentIntoManuallyClosedArm() {
        Trial trial = trial(1, 10, 10);
        Site site = site(1, trial);
        Arm arm = arm(1, trial, 5);
        EnrollmentCapacityService service = newService();

        service.closeArm(arm.id());

        assertThrows(
                IllegalStateException.class,
                () -> service.enroll(
                        1L, screening(1, patient(1), site),
                        arm, enrollmentTime(0), Set.of()
                )
        );
    }

    @Test
    void rejectsArmFromAnotherTrial() {
        Trial trial = trial(1, 10, 10);
        Trial other = trial(2, 10, 10);
        Site site = site(1, trial);
        EnrollmentCapacityService service = newService();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.enroll(
                        1L, screening(1, patient(1), site),
                        arm(2, other, 5), enrollmentTime(0), Set.of()
                )
        );
    }

    @Test
    void withdrawalIsRecordedAndAllowsReplacementWithinActiveCap() {
        Trial trial = trial(1, 1, 1);
        Site site = site(1, trial);
        Arm arm = arm(1, trial, 2);
        EnrollmentCapacityService service = newService();

        Enrollment first = service.enroll(
                1L, screening(1, patient(1), site),
                arm, enrollmentTime(0), Set.of()
        );

        service.withdraw(1L, enrollmentTime(1));

        Enrollment replacement = service.enroll(
                2L, screening(2, patient(2), site),
                arm, enrollmentTime(2), Set.of()
        );

        assertEquals(EnrollmentStatus.WITHDRAWN,
                service.enrollments().get(0).status());
        assertEquals(EnrollmentStatus.ACTIVE, replacement.status());
        assertEquals(2, service.enrollments().size());
    }

    @Test
    void returnsImmutableEnrollmentSnapshot() {
        Trial trial = trial(1, 10, 10);
        Site site = site(1, trial);
        EnrollmentCapacityService service = newService();

        service.enroll(
                1L, screening(1, patient(1), site),
                arm(1, trial, 5), enrollmentTime(0), Set.of()
        );

        List<Enrollment> snapshot = service.enrollments();

        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.clear()
        );
    }

    @Test
    void concurrentRequestsCannotExceedFinalTrialSlot() throws Exception {
        Trial trial = trial(1, 1, 1);
        Site site = site(1, trial);
        Arm arm = arm(1, trial, 1);
        EnrollmentCapacityService service = newService();

        int requestCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();

        try {
            @SuppressWarnings("unchecked")
            Future<?>[] futures = new Future<?>[requestCount];

            for (int i = 0; i < requestCount; i++) {
                final long id = i + 1L;

                futures[i] = executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();

                        service.enroll(
                                id,
                                screening(id, patient(id), site),
                                arm,
                                enrollmentTime((int) id),
                                Set.of()
                        );

                        successes.incrementAndGet();
                    } catch (IllegalStateException expected) {
                        // Capacity was already consumed by another request.
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(exception);
                    }
                });
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }

            assertEquals(1, successes.get());
            assertEquals(1, service.enrollments().stream()
                    .filter(e -> e.status() == EnrollmentStatus.ACTIVE)
                    .count());
        } finally {
            executor.shutdownNow();
        }
    }
}