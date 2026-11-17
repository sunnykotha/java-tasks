package com.example.clinicaltrial;

public record Trial(
        Long id,
        String name,
        int maxEnrollment,
        int maxEnrollmentPerSite
) {
    public Trial {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Trial ID must be positive");
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Trial name must not be blank");
        }

        if (maxEnrollment <= 0) {
            throw new IllegalArgumentException(
                    "Maximum enrollment must be positive"
            );
        }

        if (maxEnrollmentPerSite <= 0) {
            throw new IllegalArgumentException(
                    "Maximum enrollment per site must be positive"
            );
        }
    }
}