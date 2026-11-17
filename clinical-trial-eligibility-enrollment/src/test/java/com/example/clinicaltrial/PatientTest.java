package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PatientTest {

    private final LocalDate dateOfBirth = LocalDate.of(1990, 5, 15);

    @Test
    void createsPatientWithValidDetails() {
        Patient patient = new Patient(1L, dateOfBirth, Sex.FEMALE);

        assertEquals(1L, patient.id());
        assertEquals(dateOfBirth, patient.dateOfBirth());
        assertEquals(Sex.FEMALE, patient.sex());
    }

    @Test
    void rejectsNullPatientId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Patient(null, dateOfBirth, Sex.MALE)
        );
    }

    @Test
    void rejectsNonPositivePatientId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Patient(0L, dateOfBirth, Sex.MALE)
        );
    }

    @Test
    void rejectsNullDateOfBirth() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Patient(1L, null, Sex.MALE)
        );
    }

    @Test
    void rejectsNullSex() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Patient(1L, dateOfBirth, null)
        );
    }
}