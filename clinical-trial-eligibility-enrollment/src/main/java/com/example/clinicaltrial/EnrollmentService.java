package com.example.clinicaltrial;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class EnrollmentService {

    public Enrollment enroll(
            Long enrollmentId,
            Screening screening,
            Arm arm,
            LocalDateTime enrolledAt,
            List<Enrollment> existingEnrollments,
            Set<Long> conflictingTrialIds
    ) {
        if (screening == null || arm == null || enrolledAt == null) {
            throw new IllegalArgumentException(
                    "Screening, arm, and enrollment timestamp are required"
            );
        }

        if (existingEnrollments == null || conflictingTrialIds == null) {
            throw new IllegalArgumentException(
                    "Existing enrollments and conflicting trial IDs are required"
            );
        }

        if (!screening.eligible()) {
            throw new IllegalArgumentException(
                    "Only eligible patients can be enrolled"
            );
        }

        if (!arm.trial().id().equals(screening.trial().id())) {
            throw new IllegalArgumentException(
                    "Arm must belong to the screened trial"
            );
        }

        boolean conflictingEnrollment = existingEnrollments.stream()
                .filter(enrollment ->
                        enrollment.status() == EnrollmentStatus.ACTIVE)
                .anyMatch(enrollment ->
                        enrollment.patient().id().equals(
                                screening.patient().id())
                        && conflictingTrialIds.contains(
                                enrollment.trial().id()));

        if (conflictingEnrollment) {
            throw new IllegalArgumentException(
                    "Patient is already enrolled in a conflicting trial"
            );
        }

        return new Enrollment(
                enrollmentId,
                screening.patient(),
                screening.trial(),
                screening.site(),
                arm,
                enrolledAt,
                EnrollmentStatus.ACTIVE,
                null
        );
    }

    public Enrollment withdraw(
            Enrollment enrollment,
            LocalDateTime withdrawnAt
    ) {
        if (enrollment == null || withdrawnAt == null) {
            throw new IllegalArgumentException(
                    "Enrollment and withdrawal timestamp are required"
            );
        }

        if (enrollment.status() == EnrollmentStatus.WITHDRAWN) {
            throw new IllegalArgumentException(
                    "Enrollment has already been withdrawn"
            );
        }

        return new Enrollment(
                enrollment.id(),
                enrollment.patient(),
                enrollment.trial(),
                enrollment.site(),
                enrollment.arm(),
                enrollment.enrolledAt(),
                EnrollmentStatus.WITHDRAWN,
                withdrawnAt
        );
    }
}