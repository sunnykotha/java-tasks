package com.example.clinicaltrial;

import java.time.LocalDateTime;
import java.util.List;

public record Screening(
        Long id,
        Patient patient,
        Trial trial,
        Site site,
        LocalDateTime screenedAt,
        boolean eligible,
        List<String> failedCriteria
) {
    public Screening {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "Screening ID must be positive"
            );
        }

        if (patient == null || trial == null || site == null) {
            throw new IllegalArgumentException(
                    "Patient, trial, and site must not be null"
            );
        }

        if (screenedAt == null) {
            throw new IllegalArgumentException(
                    "Screening timestamp must not be null"
            );
        }

        if (!site.trial().id().equals(trial.id())) {
            throw new IllegalArgumentException(
                    "Site must belong to the screening trial"
            );
        }

        if (failedCriteria == null) {
            throw new IllegalArgumentException(
                    "Failed criteria must not be null"
            );
        }

        failedCriteria = List.copyOf(failedCriteria);

        if (eligible && !failedCriteria.isEmpty()) {
            throw new IllegalArgumentException(
                    "Eligible screening cannot contain failed criteria"
            );
        }

        if (!eligible && failedCriteria.isEmpty()) {
            throw new IllegalArgumentException(
                    "Failed screening must record failed criteria"
            );
        }
    }
}