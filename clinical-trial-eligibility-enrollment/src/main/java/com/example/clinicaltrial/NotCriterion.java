package com.example.clinicaltrial;

public record NotCriterion(Criterion criterion) implements Criterion {

    public NotCriterion {
        if (criterion == null) {
            throw new IllegalArgumentException(
                    "NOT requires a criterion"
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

        EvaluationResult result = criterion.evaluate(context);

        return result.passed()
                ? EvaluationResult.fail("NOT(" + criterion + ")")
                : EvaluationResult.pass();
    }
}