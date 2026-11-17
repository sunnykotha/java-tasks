package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LabValueCriterionTest {

    private final LocalDate evaluationDate =
            LocalDate.of(2026, 1, 31);

    private Patient patient(long id) {
        return new Patient(
                id,
                LocalDate.of(1990, 5, 15),
                Sex.FEMALE
        );
    }

    private EvaluationContext context(Patient patient) {
        return new EvaluationContext(patient, evaluationDate);
    }

    private LabResult result(
            Patient patient,
            String testName,
            String value,
            LocalDate collectedAt
    ) {
        return new LabResult(
                1L,
                patient,
                testName,
                new BigDecimal(value),
                "%",
                collectedAt
        );
    }

    private LabValueCriterion criterion(LabResult result) {
        return new LabValueCriterion(
                "HBA1C_7_10",
                result,
                "HbA1c",
                new BigDecimal("7.0"),
                new BigDecimal("10.0"),
                30
        );
    }

    @Test
    void acceptsValueWithinRangeAndResultWithinThirtyDays() {
        LabResult result = result(
                patient(1), "HbA1c", "8.0",
                LocalDate.of(2026, 1, 1)
        );

        assertTrue(criterion(result).evaluate(context(patient(1))).passed());
    }

    @Test
    void acceptsMinimumValueBoundary() {
        LabResult result = result(
                patient(1), "HbA1c", "7.0", evaluationDate
        );

        assertTrue(criterion(result).evaluate(context(patient(1))).passed());
    }

    @Test
    void acceptsMaximumValueBoundary() {
        LabResult result = result(
                patient(1), "HbA1c", "10.0", evaluationDate
        );

        assertTrue(criterion(result).evaluate(context(patient(1))).passed());
    }

    @Test
    void rejectsValueBelowRange() {
        LabResult result = result(
                patient(1), "HbA1c", "6.9", evaluationDate
        );

        assertEquals(
                List.of("HBA1C_7_10"),
                criterion(result).evaluate(context(patient(1))).failedCriteria()
        );
    }

    @Test
    void rejectsValueAboveRange() {
        LabResult result = result(
                patient(1), "HbA1c", "10.1", evaluationDate
        );

        assertFalse(criterion(result).evaluate(context(patient(1))).passed());
    }

    @Test
    void acceptsResultExactlyThirtyDaysOld() {
        LabResult result = result(
                patient(1), "HbA1c", "8.0",
                LocalDate.of(2026, 1, 1)
        );

        assertTrue(criterion(result).evaluate(context(patient(1))).passed());
    }

    @Test
    void rejectsResultThirtyOneDaysOld() {
        LabResult result = result(
                patient(1), "HbA1c", "8.0",
                LocalDate.of(2025, 12, 31)
        );

        assertFalse(criterion(result).evaluate(context(patient(1))).passed());
    }

    @Test
    void rejectsResultFromAnotherPatient() {
        LabResult result = result(
                patient(2), "HbA1c", "8.0", evaluationDate
        );

        assertFalse(criterion(result).evaluate(context(patient(1))).passed());
    }

    @Test
    void rejectsWrongTestName() {
        LabResult result = result(
                patient(1), "Glucose", "8.0", evaluationDate
        );

        assertFalse(criterion(result).evaluate(context(patient(1))).passed());
    }

    @Test
    void rejectsFutureDatedResult() {
        LabResult result = result(
                patient(1), "HbA1c", "8.0",
                LocalDate.of(2026, 2, 1)
        );

        assertFalse(criterion(result).evaluate(context(patient(1))).passed());
    }
}