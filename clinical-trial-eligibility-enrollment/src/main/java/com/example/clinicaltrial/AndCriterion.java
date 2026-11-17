package com.example.clinicaltrial;

import java.util.List;

public record AndCriterion(List<Criterion> criteria) implements Criterion {

    public AndCriterion {
        if (criteria == null || criteria.isEmpty()) {
            throw new IllegalArgumentException(
                    "AND requires at least one criterion"
            );
        }

        criteria = List.copyOf(criteria);

        if (criteria.stream().anyMatch(c -> c == null)) {
            throw new IllegalArgumentException(
                    "Criteria must not contain null"
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

        List<String> failures = criteria.stream()
                .map(c -> c.evaluate(context))
                .filter(result -> !result.passed())
                .flatMap(result -> result.failedCriteria().stream())
                .toList();

        return failures.isEmpty()
                ? EvaluationResult.pass()
                : new EvaluationResult(false, failures);
    }
}