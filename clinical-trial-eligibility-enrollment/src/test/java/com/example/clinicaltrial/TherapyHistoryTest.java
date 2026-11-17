package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class TherapyHistoryTest {

    private Patient patient() {
        return new Patient(
                1L,
                LocalDate.of(1990, 5, 15),
                Sex.FEMALE
        );
    }

    @Test
    void createsTherapyHistoryWithValidDetails() {
        TherapyHistory history = new TherapyHistory(
                1L,
                patient(),
                "Insulin",
                LocalDate.of(2025, 1, 1)
        );

        assertEquals("Insulin", history.therapyName());
        assertEquals(LocalDate.of(2025, 1, 1), history.lastUsedAt());
    }

    @Test
    void rejectsNullPatient() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TherapyHistory(
                        1L, null, "Insulin", LocalDate.of(2025, 1, 1)
                )
        );
    }

    @Test
    void rejectsBlankTherapyName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TherapyHistory(
                        1L, patient(), " ", LocalDate.of(2025, 1, 1)
                )
        );
    }

    @Test
    void rejectsNullLastUseDate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TherapyHistory(1L, patient(), "Insulin", null)
        );
    }
}