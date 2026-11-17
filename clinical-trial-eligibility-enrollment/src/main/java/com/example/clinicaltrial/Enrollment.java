package com.example.clinicaltrial;

import java.time.LocalDateTime;

public record Enrollment(
        Long id,
        Patient patient,
        Trial trial,
        Site site,
        Arm arm,
        LocalDateTime enrolledAt,
        EnrollmentStatus status,
        LocalDateTime withdrawnAt
) {
    public Enrollment {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "Enrollment ID must be positive"
            );
        }

        if (patient == null || trial == null || site == null || arm == null) {
            throw new IllegalArgumentException(
                    "Patient, trial, site, and arm must not be null"
            );
        }

        if (enrolledAt == null || status == null) {
            throw new IllegalArgumentException(
                    "Enrollment timestamp and status must not be null"
            );
        }

        if (!site.trial().id().equals(trial.id())) {
            throw new IllegalArgumentException(
                    "Site must belong to the enrollment trial"
            );
        }

        if (!arm.trial().id().equals(trial.id())) {
            throw new IllegalArgumentException(
                    "Arm must belong to the enrollment trial"
            );
        }

        if (status == EnrollmentStatus.ACTIVE && withdrawnAt != null) {
            throw new IllegalArgumentException(
                    "Active enrollment cannot have a withdrawal timestamp"
            );
        }

        if (status == EnrollmentStatus.WITHDRAWN) {
            if (withdrawnAt == null) {
                throw new IllegalArgumentException(
                        "Withdrawn enrollment requires a withdrawal timestamp"
                );
            }

            if (withdrawnAt.isBefore(enrolledAt)) {
                throw new IllegalArgumentException(
                        "Withdrawal cannot precede enrollment"
                );
            }
        }
    }
}