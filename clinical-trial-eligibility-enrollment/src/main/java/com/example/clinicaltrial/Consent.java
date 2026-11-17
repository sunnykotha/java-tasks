package com.example.clinicaltrial;

import java.time.LocalDateTime;

public record Consent(
        Long id,
        Patient patient,
        LocalDateTime consentedAt
) {
    public Consent {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Consent ID must be positive");
        }

        if (patient == null) {
            throw new IllegalArgumentException("Patient must not be null");
        }

        if (consentedAt == null) {
            throw new IllegalArgumentException(
                    "Consent timestamp must not be null"
            );
        }
    }
}