package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgeRangeCriterionTest {

    private EvaluationContext context(
            LocalDate dateOfBirth,
            LocalDate evaluationDate
    ) {
        Patient patient = new Patient(1L, dateOfBirth, Sex.FEMALE);
        return new EvaluationContext(patient, evaluationDate);
    }

    private AgeRangeCriterion criterion() {
        return new AgeRangeCriterion("AGE_18_65", 18, 65);
    }

    @Test
    void acceptsMinimumAgeBoundary() {
        EvaluationResult result = criterion().evaluate(context(
                LocalDate.of(2008, 1, 10),
                LocalDate.of(2026, 1, 10)
        ));

        assertTrue(result.passed());
    }

    @Test
    void acceptsMaximumAgeBoundary() {
        EvaluationResult result = criterion().evaluate(context(
                LocalDate.of(1961, 1, 10),
                LocalDate.of(2026, 1, 10)
        ));

        assertTrue(result.passed());
    }

    @Test
    void rejectsPatientOneDayBelowMinimumAge() {
        EvaluationResult result = criterion().evaluate(context(
                LocalDate.of(2008, 1, 11),
                LocalDate.of(2026, 1, 10)
        ));

        assertFalse(result.passed());
        assertEquals(List.of("AGE_18_65"), result.failedCriteria());
    }

    @Test
    void rejectsPatientOneDayAboveMaximumAge() {
        EvaluationResult result = criterion().evaluate(context(
                LocalDate.of(1960, 1, 9),
                LocalDate.of(2026, 1, 10)
        ));

        assertFalse(result.passed());
        assertEquals(List.of("AGE_18_65"), result.failedCriteria());
    }

    @Test
    void usesEvaluationDateInsteadOfCurrentDate() {
        EvaluationResult result = criterion().evaluate(context(
                LocalDate.of(2008, 1, 11),
                LocalDate.of(2026, 1, 10)
        ));

        assertFalse(result.passed());
    }

    @Test
    void rejectsInvalidAgeRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AgeRangeCriterion("AGE", 66, 18)
        );
    }
}