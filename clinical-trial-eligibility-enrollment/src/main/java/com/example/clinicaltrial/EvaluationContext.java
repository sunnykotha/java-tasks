package com.example.clinicaltrial;

import java.time.LocalDate;

public record EvaluationContext(
        Patient patient,
        LocalDate evaluationDate
) {
    public EvaluationContext {
        if (patient == null) {
            throw new IllegalArgumentException("Patient must not be null");
        }

        if (evaluationDate == null) {
            throw new IllegalArgumentException(
                    "Evaluation date must not be null"
            );
        }
    }
}