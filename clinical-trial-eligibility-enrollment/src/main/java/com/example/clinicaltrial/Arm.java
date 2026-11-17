package com.example.clinicaltrial;

public record Arm(
        Long id,
        String name,
        Trial trial,
        int maxEnrollment
) {
    public Arm {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Arm ID must be positive");
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Arm name must not be blank");
        }

        if (trial == null) {
            throw new IllegalArgumentException("Trial must not be null");
        }

        if (maxEnrollment <= 0) {
            throw new IllegalArgumentException(
                    "Maximum arm enrollment must be positive"
            );
        }
    }
}