package com.example.clinicaltrial;

public record Site(
        Long id,
        String name,
        Trial trial
) {
    public Site {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Site ID must be positive");
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Site name must not be blank");
        }

        if (trial == null) {
            throw new IllegalArgumentException("Trial must not be null");
        }
    }
}