package com.example.clinicaltrial;

import java.time.LocalDateTime;

public final class ScreeningService {

    public Screening screen(
            Long screeningId,
            Patient patient,
            Trial trial,
            Site site,
            Consent consent,
            LocalDateTime procedureAt,
            Criterion eligibilityCriteria
    ) {
        if (patient == null || trial == null || site == null) {
            throw new IllegalArgumentException(
                    "Patient, trial, and site must not be null"
            );
        }

        if (consent == null) {
            throw new IllegalArgumentException(
                    "Recorded consent is required before screening"
            );
        }

        if (procedureAt == null) {
            throw new IllegalArgumentException(
                    "Procedure timestamp must not be null"
            );
        }

        if (eligibilityCriteria == null) {
            throw new IllegalArgumentException(
                    "Eligibility criteria must not be null"
            );
        }

        if (!consent.patient().id().equals(patient.id())) {
            throw new IllegalArgumentException(
                    "Consent must belong to the screened patient"
            );
        }

        if (!consent.consentedAt().isBefore(procedureAt)) {
            throw new IllegalArgumentException(
                    "Consent must be recorded before screening procedures"
            );
        }

        if (!site.trial().id().equals(trial.id())) {
            throw new IllegalArgumentException(
                    "Site must belong to the screening trial"
            );
        }

        EvaluationContext context = new EvaluationContext(
                patient,
                procedureAt.toLocalDate()
        );

        EvaluationResult result = eligibilityCriteria.evaluate(context);

        return new Screening(
                screeningId,
                patient,
                trial,
                site,
                procedureAt,
                result.passed(),
                result.failedCriteria()
        );
    }
}