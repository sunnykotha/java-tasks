package com.example.clinicaltrial;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LabResult(
        Long id,
        Patient patient,
        String testName,
        BigDecimal value,
        String unit,
        LocalDate collectedAt
) {
    public LabResult {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Lab result ID must be positive");
        }

        if (patient == null) {
            throw new IllegalArgumentException("Patient must not be null");
        }

        if (testName == null || testName.isBlank()) {
            throw new IllegalArgumentException("Test name must not be blank");
        }

        if (value == null) {
            throw new IllegalArgumentException("Lab value must not be null");
        }

        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("Unit must not be blank");
        }

        if (collectedAt == null) {
            throw new IllegalArgumentException(
                    "Collection date must not be null"
            );
        }
    }
}