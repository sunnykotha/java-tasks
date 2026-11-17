package com.example.clinicaltrial;

import java.util.Objects;
import java.util.function.Predicate;

public final class AtomicCriterion implements Criterion {

    private final String id;
    private final Predicate<EvaluationContext> condition;

    public AtomicCriterion(
            String id,
            Predicate<EvaluationContext> condition
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Criterion ID must not be blank"
            );
        }

        this.id = id;
        this.condition = Objects.requireNonNull(
                condition,
                "Condition must not be null"
        );
    }

    public String id() {
        return id;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "Evaluation context must not be null"
            );
        }

        return condition.test(context)
                ? EvaluationResult.pass()
                : EvaluationResult.fail(id);
    }
}