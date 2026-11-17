package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EvaluationResultTest {

    @Test
    void createsPassingResultWithoutFailures() {
        EvaluationResult result = EvaluationResult.pass();

        assertTrue(result.passed());
        assertTrue(result.failedCriteria().isEmpty());
    }

    @Test
    void createsFailingResultWithCriterionId() {
        EvaluationResult result = EvaluationResult.fail("AGE_RANGE");

        assertFalse(result.passed());
        assertEquals(List.of("AGE_RANGE"), result.failedCriteria());
    }

    @Test
    void rejectsPassingResultWithFailedCriteria() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EvaluationResult(false, List.of())
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new EvaluationResult(true, List.of("AGE_RANGE"))
        );
    }

    @Test
    void rejectsNullFailureList() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EvaluationResult(true, null)
        );
    }

    @Test
    void failedCriteriaListIsImmutable() {
        List<String> failures = new ArrayList<>();
        failures.add("AGE_RANGE");

        EvaluationResult result = new EvaluationResult(false, failures);
        failures.add("HBA1C_RANGE");

        assertEquals(List.of("AGE_RANGE"), result.failedCriteria());

        assertThrows(
                UnsupportedOperationException.class,
                () -> result.failedCriteria().add("OTHER")
        );
    }

    @Test
    void rejectsBlankCriterionId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EvaluationResult.fail(" ")
        );
    }
}