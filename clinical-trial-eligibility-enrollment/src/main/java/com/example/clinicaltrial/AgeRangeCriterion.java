package com.example.clinicaltrial;

import java.time.Period;

public record AgeRangeCriterion(
        String id,
        int minimumAge,
        int maximumAge
) implements Criterion {

    public AgeRangeCriterion {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Criterion ID must not be blank"
            );
        }

        if (minimumAge < 0 || maximumAge < minimumAge) {
            throw new IllegalArgumentException(
                    "Invalid age range"
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

        int age = Period.between(
                context.patient().dateOfBirth(),
                context.evaluationDate()
        ).getYears();

        return age >= minimumAge && age <= maximumAge
                ? EvaluationResult.pass()
                : EvaluationResult.fail(id);
    }
}