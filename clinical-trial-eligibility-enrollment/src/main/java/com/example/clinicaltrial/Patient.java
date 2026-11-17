package com.example.clinicaltrial;

import java.time.LocalDate;

public record Patient(
        Long id,
        LocalDate dateOfBirth,
        Sex sex
) {
    public Patient {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Patient ID must be positive");
        }

        if (dateOfBirth == null) {
            throw new IllegalArgumentException(
                    "Date of birth must not be null"
            );
        }

        if (sex == null) {
            throw new IllegalArgumentException("Sex must not be null");
        }
    }
}