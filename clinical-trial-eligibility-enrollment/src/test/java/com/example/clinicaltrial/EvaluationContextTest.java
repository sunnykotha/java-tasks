package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class EvaluationContextTest {

    private Patient validPatient() {
        return new Patient(
                1L,
                LocalDate.of(1990, 5, 15),
                Sex.FEMALE
        );
    }

    @Test
    void createsContextWithPatientAndEvaluationDate() {
        LocalDate date = LocalDate.of(2026, 1, 10);

        EvaluationContext context =
                new EvaluationContext(validPatient(), date);

        assertNotNull(context.patient());
        assertEquals(date, context.evaluationDate());
    }

    @Test
    void rejectsNullPatient() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EvaluationContext(
                        null,
                        LocalDate.of(2026, 1, 10)
                )
        );
    }

    @Test
    void rejectsNullEvaluationDate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EvaluationContext(validPatient(), null)
        );
    }
}