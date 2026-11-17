package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CorrectedLabAfterEnrollmentTest {

    @Test
    void correctedLabResultDoesNotSilentlyRewriteExistingScreeningOrEnrollment() {
        Patient patient = new Patient(
                1L, LocalDate.of(1990, 5, 15), Sex.FEMALE
        );
        Trial trial = new Trial(1L, "Diabetes Study", 10, 10);
        Site site = new Site(1L, "Site A", trial);
        Arm arm = new Arm(1L, "Arm A", trial, 10);

        LocalDate evaluationDate = LocalDate.of(2026, 1, 31);
        LocalDateTime procedureAt = evaluationDate.atTime(10, 0);

        LabResult originalLab = new LabResult(
                1L, patient, "HbA1c", new BigDecimal("8.0"),
                "%", LocalDate.of(2026, 1, 20)
        );

        Criterion originalCriteria = new LabValueCriterion(
                "HBA1C_7_10", originalLab, "HbA1c",
                new BigDecimal("7.0"), new BigDecimal("10.0"), 30
        );

        Screening screening = new ScreeningService().screen(
                1L, patient, trial, site,
                new Consent(1L, patient, procedureAt.minusHours(1)),
                procedureAt, originalCriteria
        );

        assertTrue(screening.eligible());

        Enrollment enrollment = new EnrollmentService().enroll(
                1L, screening, arm, procedureAt.plusMinutes(5),
                List.of(), Set.of()
        );

        LabResult correctedLab = new LabResult(
                1L, patient, "HbA1c", new BigDecimal("6.5"),
                "%", LocalDate.of(2026, 1, 20)
        );

        Criterion correctedCriteria = new LabValueCriterion(
                "HBA1C_7_10", correctedLab, "HbA1c",
                new BigDecimal("7.0"), new BigDecimal("10.0"), 30
        );

        EvaluationResult correctedResult = correctedCriteria.evaluate(
                new EvaluationContext(patient, evaluationDate)
        );

        assertFalse(correctedResult.passed());
        assertTrue(screening.eligible());
        assertTrue(screening.failedCriteria().isEmpty());
        assertEquals(EnrollmentStatus.ACTIVE, enrollment.status());
    }
}