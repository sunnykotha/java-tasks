package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrialTest {

    @Test
    void createsTrialWithValidDetails() {
        Trial trial = new Trial(1L, "Diabetes Study", 100, 25);

        assertEquals(1L, trial.id());
        assertEquals("Diabetes Study", trial.name());
        assertEquals(100, trial.maxEnrollment());
        assertEquals(25, trial.maxEnrollmentPerSite());
    }

    @Test
    void rejectsNullTrialId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Trial(null, "Diabetes Study", 100, 25)
        );
    }

    @Test
    void rejectsBlankTrialName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Trial(1L, " ", 100, 25)
        );
    }

    @Test
    void rejectsNonPositiveOverallEnrollmentLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Trial(1L, "Diabetes Study", 0, 25)
        );
    }

    @Test
    void rejectsNonPositiveSiteEnrollmentLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Trial(1L, "Diabetes Study", 100, 0)
        );
    }
}