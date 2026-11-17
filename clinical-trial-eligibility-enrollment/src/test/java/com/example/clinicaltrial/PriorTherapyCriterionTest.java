package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PriorTherapyCriterionTest {

    private final LocalDate evaluationDate =
            LocalDate.of(2026, 1, 15);

    private Patient patient(long id) {
        return new Patient(
                id,
                LocalDate.of(1990, 5, 15),
                Sex.FEMALE
        );
    }

    private EvaluationContext context(Patient patient) {
        return new EvaluationContext(patient, evaluationDate);
    }

    private TherapyHistory history(
            long id,
            Patient patient,
            String therapy,
            LocalDate lastUsed
    ) {
        return new TherapyHistory(id, patient, therapy, lastUsed);
    }

    private PriorTherapyCriterion criterion(
            List<TherapyHistory> history
    ) {
        return new PriorTherapyCriterion(
                "NO_RECENT_INSULIN",
                "Insulin",
                6,
                history
        );
    }

    @Test
    void passesWhenTherapyHistoryIsEmpty() {
        assertTrue(criterion(List.of())
                .evaluate(context(patient(1))).passed());
    }

    @Test
    void passesWhenTherapyWasUsedBeforeWashoutWindow() {
        TherapyHistory oldUse = history(
                1, patient(1), "Insulin",
                LocalDate.of(2025, 7, 14)
        );

        assertTrue(criterion(List.of(oldUse))
                .evaluate(context(patient(1))).passed());
    }

    @Test
    void failsWhenTherapyWasUsedWithinWashoutWindow() {
        TherapyHistory recentUse = history(
                1, patient(1), "Insulin",
                LocalDate.of(2025, 10, 1)
        );

        assertFalse(criterion(List.of(recentUse))
                .evaluate(context(patient(1))).passed());
    }

    @Test
    void failsWhenTherapyWasUsedExactlyAtCutoff() {
        TherapyHistory cutoffUse = history(
                1, patient(1), "Insulin",
                LocalDate.of(2025, 7, 15)
        );

        assertFalse(criterion(List.of(cutoffUse))
                .evaluate(context(patient(1))).passed());
    }

    @Test
    void ignoresUnrelatedTherapy() {
        TherapyHistory otherTherapy = history(
                1, patient(1), "Metformin",
                LocalDate.of(2025, 12, 1)
        );

        assertTrue(criterion(List.of(otherTherapy))
                .evaluate(context(patient(1))).passed());
    }

    @Test
    void ignoresAnotherPatientsTherapyHistory() {
        TherapyHistory otherPatientUse = history(
                1, patient(2), "Insulin",
                LocalDate.of(2025, 12, 1)
        );

        assertTrue(criterion(List.of(otherPatientUse))
                .evaluate(context(patient(1))).passed());
    }

    @Test
    void matchesTherapyNameWithoutCaseSensitivity() {
        TherapyHistory recentUse = history(
                1, patient(1), "INSULIN",
                LocalDate.of(2025, 12, 1)
        );

        assertFalse(criterion(List.of(recentUse))
                .evaluate(context(patient(1))).passed());
    }

    @Test
    void failsWhenMatchingTherapyDateIsInTheFuture() {
        TherapyHistory futureUse = history(
                1, patient(1), "Insulin",
                LocalDate.of(2026, 2, 1)
        );

        assertFalse(criterion(List.of(futureUse))
                .evaluate(context(patient(1))).passed());
    }

    @Test
    void rejectsNegativeWashoutPeriod() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PriorTherapyCriterion(
                        "NO_INSULIN", "Insulin", -1, List.of()
                )
        );
    }

    @Test
    void rejectsNullHistoryList() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PriorTherapyCriterion(
                        "NO_INSULIN", "Insulin", 6, null
                )
        );
    }
}