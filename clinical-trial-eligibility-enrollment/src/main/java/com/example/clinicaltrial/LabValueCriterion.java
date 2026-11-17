package com.example.clinicaltrial;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;

public record LabValueCriterion(
        String id,
        LabResult labResult,
        String requiredTestName,
        BigDecimal minimumValue,
        BigDecimal maximumValue,
        int maximumAgeDays
) implements Criterion {

    public LabValueCriterion {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Criterion ID must not be blank"
            );
        }

        if (labResult == null) {
            throw new IllegalArgumentException(
                    "Lab result must not be null"
            );
        }

        if (requiredTestName == null || requiredTestName.isBlank()) {
            throw new IllegalArgumentException(
                    "Required test name must not be blank"
            );
        }

        if (minimumValue == null || maximumValue == null
                || minimumValue.compareTo(maximumValue) > 0) {
            throw new IllegalArgumentException("Invalid lab value range");
        }

        if (maximumAgeDays < 0) {
            throw new IllegalArgumentException(
                    "Maximum lab age must not be negative"
            );
        }
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "Evaluation context must not be null"
            );
        }

        if (!labResult.patient().id().equals(context.patient().id())) {
            return EvaluationResult.fail(id);
        }

        if (!labResult.testName().equals(requiredTestName)) {
            return EvaluationResult.fail(id);
        }

        long ageDays = ChronoUnit.DAYS.between(
                labResult.collectedAt(),
                context.evaluationDate()
        );

        if (ageDays < 0 || ageDays > maximumAgeDays) {
            return EvaluationResult.fail(id);
        }

        boolean inRange =
                labResult.value().compareTo(minimumValue) >= 0
                && labResult.value().compareTo(maximumValue) <= 0;

        return inRange
                ? EvaluationResult.pass()
                : EvaluationResult.fail(id);
    }
}