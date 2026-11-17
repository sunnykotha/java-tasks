package com.example.clinicaltrial;

import java.time.LocalDate;
import java.util.List;

public record PriorTherapyCriterion(
        String id,
        String therapyName,
        int washoutMonths,
        List<TherapyHistory> therapyHistory
) implements Criterion {

    public PriorTherapyCriterion {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Criterion ID must not be blank"
            );
        }

        if (therapyName == null || therapyName.isBlank()) {
            throw new IllegalArgumentException(
                    "Therapy name must not be blank"
            );
        }

        if (washoutMonths < 0) {
            throw new IllegalArgumentException(
                    "Washout months must not be negative"
            );
        }

        if (therapyHistory == null) {
            throw new IllegalArgumentException(
                    "Therapy history must not be null"
            );
        }

        therapyHistory = List.copyOf(therapyHistory);
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "Evaluation context must not be null"
            );
        }

        LocalDate cutoff = context.evaluationDate()
                .minusMonths(washoutMonths);

        boolean recentOrFutureUse = therapyHistory.stream()
                .filter(history ->
                        history.patient().id().equals(context.patient().id()))
                .filter(history ->
                        history.therapyName().equalsIgnoreCase(therapyName))
                .anyMatch(history ->
                        !history.lastUsedAt().isBefore(cutoff));

        return recentOrFutureUse
                ? EvaluationResult.fail(id)
                : EvaluationResult.pass();
    }
}