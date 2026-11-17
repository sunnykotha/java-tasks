package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnrollmentRepositoryTest {

    @Test
    void requiresDatabase() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new EnrollmentRepository(null)
        );
    }

    @Test
    void rejectsNullEnrollmentIdForExists() {
        ClinicalTrialDatabase database = new ClinicalTrialDatabase(
            "jdbc:h2:mem:enrollment_repository_validation;DB_CLOSE_DELAY=-1"
        );
        EnrollmentRepository repository = new EnrollmentRepository(database);

        assertThrows(
            IllegalArgumentException.class,
            () -> repository.exists(null)
        );
    }

    @Test
    void reportsMissingEnrollment() {
        ClinicalTrialDatabase database = new ClinicalTrialDatabase(
            "jdbc:h2:mem:enrollment_repository_missing;DB_CLOSE_DELAY=-1"
        );
        EnrollmentRepository repository = new EnrollmentRepository(database);

        assertFalse(repository.exists(987654L));
    }
}