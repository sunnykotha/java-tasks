package com.example.clinicaltrial;

import java.util.List;

public record OrCriterion(List<Criterion> criteria) implements Criterion {

    public OrCriterion {
        if (criteria == null || criteria.isEmpty()) {
            throw new IllegalArgumentException(
                    "OR requires at least one criterion"
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

        List<EvaluationResult> results = criteria.stream()
                .map(c -> c.evaluate(context))
                .toList();

        if (results.stream().anyMatch(EvaluationResult::passed)) {
            return EvaluationResult.pass();
        }

        List<String> failures = results.stream()
                .flatMap(result -> result.failedCriteria().stream())
                .toList();

        return new EvaluationResult(false, failures);
    }
}