package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ConsentTest {

    private Patient patient() {
        return new Patient(
                1L,
                LocalDate.of(1990, 5, 15),
                Sex.FEMALE
        );
    }

    @Test
    void createsConsentWithValidDetails() {
        LocalDateTime time = LocalDateTime.of(2026, 1, 10, 9, 0);

        Consent consent = new Consent(1L, patient(), time);

        assertEquals(1L, consent.id());
        assertEquals(patient(), consent.patient());
        assertEquals(time, consent.consentedAt());
    }

    @Test
    void rejectsNonPositiveConsentId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Consent(
                        0L, patient(),
                        LocalDateTime.of(2026, 1, 10, 9, 0)
                )
        );
    }

    @Test
    void rejectsNullPatient() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Consent(
                        1L, null,
                        LocalDateTime.of(2026, 1, 10, 9, 0)
                )
        );
    }

    @Test
    void rejectsNullConsentTimestamp() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Consent(1L, patient(), null)
        );
    }
}