package com.example.clinicaltrial;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EnrollmentCapacityService {

    private final EnrollmentService enrollmentService = new EnrollmentService();
    private final List<Enrollment> enrollments = new ArrayList<>();
    private final Set<Long> closedArmIds = new HashSet<>();
    private final Set<Long> closedSiteIds = new HashSet<>();

    public synchronized Enrollment enroll(
            Long enrollmentId,
            Screening screening,
            Arm arm,
            LocalDateTime enrolledAt,
            Set<Long> conflictingTrialIds
    ) {
        if (screening == null || arm == null || enrolledAt == null
                || conflictingTrialIds == null) {
            throw new IllegalArgumentException(
                    "Screening, arm, timestamp, and conflict IDs are required"
            );
        }

        Trial trial = screening.trial();
        Site site = screening.site();

        if (closedSiteIds.contains(site.id())) {
            throw new IllegalStateException("Site is closed");
        }

        if (!arm.trial().id().equals(trial.id())) {
            throw new IllegalArgumentException(
                    "Arm must belong to the screening trial"
            );
        }

        if (closedArmIds.contains(arm.id())) {
            throw new IllegalStateException("Arm is closed");
        }

        long trialCount = enrollments.stream()
                .filter(e -> e.trial().id().equals(trial.id()))
                .filter(e -> e.status() == EnrollmentStatus.ACTIVE)
                .count();

        if (trialCount >= trial.maxEnrollment()) {
            throw new IllegalStateException(
                    "Overall trial enrollment cap reached"
            );
        }

        long siteCount = enrollments.stream()
                .filter(e -> e.trial().id().equals(trial.id()))
                .filter(e -> e.site().id().equals(site.id()))
                .filter(e -> e.status() == EnrollmentStatus.ACTIVE)
                .count();

        if (siteCount >= trial.maxEnrollmentPerSite()) {
            throw new IllegalStateException(
                    "Site enrollment cap reached"
            );
        }

        long armCount = enrollments.stream()
                .filter(e -> e.trial().id().equals(trial.id()))
                .filter(e -> e.arm().id().equals(arm.id()))
                .filter(e -> e.status() == EnrollmentStatus.ACTIVE)
                .count();

        if (armCount >= arm.maxEnrollment()) {
            closedArmIds.add(arm.id());
            throw new IllegalStateException("Arm enrollment cap reached");
        }

        Enrollment enrollment = enrollmentService.enroll(
                enrollmentId,
                screening,
                arm,
                enrolledAt,
                List.copyOf(enrollments),
                conflictingTrialIds
        );

        enrollments.add(enrollment);

        if (siteCount + 1 >= trial.maxEnrollmentPerSite()) {
            closedSiteIds.add(site.id());
        }

        if (armCount + 1 >= arm.maxEnrollment()) {
            closedArmIds.add(arm.id());
        }

        return enrollment;
    }

    public synchronized Enrollment withdraw(
            Long enrollmentId,
            LocalDateTime withdrawnAt
    ) {
        for (int i = 0; i < enrollments.size(); i++) {
            Enrollment existing = enrollments.get(i);

            if (existing.id().equals(enrollmentId)) {
                Enrollment withdrawn = enrollmentService.withdraw(
                        existing, withdrawnAt
                );
                enrollments.set(i, withdrawn);

                long activeAtSite = enrollments.stream()
                        .filter(e -> e.trial().id().equals(existing.trial().id()))
                        .filter(e -> e.site().id().equals(existing.site().id()))
                        .filter(e -> e.status() == EnrollmentStatus.ACTIVE)
                        .count();

                if (activeAtSite < existing.trial().maxEnrollmentPerSite()) {
                    closedSiteIds.remove(existing.site().id());
                }

                return withdrawn;
            }
        }

        throw new IllegalArgumentException("Enrollment not found");
    }

    public synchronized void closeArm(Long armId) {
        if (armId == null || armId <= 0) {
            throw new IllegalArgumentException("Arm ID must be positive");
        }

        closedArmIds.add(armId);
    }

    public synchronized boolean isArmClosed(Long armId) {
        return closedArmIds.contains(armId);
    }

    public synchronized void closeSite(Long siteId) {
        if (siteId == null || siteId <= 0) {
            throw new IllegalArgumentException("Site ID must be positive");
        }
        closedSiteIds.add(siteId);
    }

    public synchronized boolean isSiteClosed(Long siteId) {
        return closedSiteIds.contains(siteId);
    }
    public synchronized List<Enrollment> enrollments() {
        return List.copyOf(enrollments);
    }
}