package com.example.clinicaltrial;

@FunctionalInterface
public interface Criterion {

    EvaluationResult evaluate(EvaluationContext context);
}