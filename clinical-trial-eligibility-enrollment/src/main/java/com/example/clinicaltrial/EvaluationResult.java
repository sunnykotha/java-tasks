package com.example.clinicaltrial;

import java.util.List;

public record EvaluationResult(
        boolean passed,
        List<String> failedCriteria
) {
    public EvaluationResult {
        if (failedCriteria == null) {
            throw new IllegalArgumentException(
                    "Failed criteria must not be null"
            );
        }

        failedCriteria = List.copyOf(failedCriteria);

        if (passed && !failedCriteria.isEmpty()) {
            throw new IllegalArgumentException(
                    "A passing result cannot contain failed criteria"
            );
        }

        if (!passed && failedCriteria.isEmpty()) {
            throw new IllegalArgumentException(
                    "A failing result must identify failed criteria"
            );
        }
    }

    public static EvaluationResult pass() {
        return new EvaluationResult(true, List.of());
    }

    public static EvaluationResult fail(String criterionId) {
        if (criterionId == null || criterionId.isBlank()) {
            throw new IllegalArgumentException(
                    "Criterion ID must not be blank"
            );
        }

        return new EvaluationResult(false, List.of(criterionId));
    }
}