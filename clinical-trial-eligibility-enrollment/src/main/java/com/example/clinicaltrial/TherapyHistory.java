package com.example.clinicaltrial;

import java.time.LocalDate;

public record TherapyHistory(
        Long id,
        Patient patient,
        String therapyName,
        LocalDate lastUsedAt
) {
    public TherapyHistory {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "Therapy history ID must be positive"
            );
        }

        if (patient == null) {
            throw new IllegalArgumentException(
                    "Patient must not be null"
            );
        }

        if (therapyName == null || therapyName.isBlank()) {
            throw new IllegalArgumentException(
                    "Therapy name must not be blank"
            );
        }

        if (lastUsedAt == null) {
            throw new IllegalArgumentException(
                    "Last-use date must not be null"
            );
        }
    }
}