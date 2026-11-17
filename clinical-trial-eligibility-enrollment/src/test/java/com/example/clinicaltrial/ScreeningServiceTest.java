package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScreeningServiceTest {

    private final ScreeningService service = new ScreeningService();

    private Patient patient(long id) {
        return new Patient(
                id,
                LocalDate.of(1990, 5, 15),
                Sex.FEMALE
        );
    }

    private Trial trial() {
        return new Trial(1L, "Diabetes Study", 100, 25);
    }

    private Site site(Trial trial) {
        return new Site(1L, "Site A", trial);
    }

    private Consent consent(Patient patient, LocalDateTime time) {
        return new Consent(1L, patient, time);
    }

    private LocalDateTime procedureTime() {
        return LocalDateTime.of(2026, 1, 10, 10, 0);
    }

    @Test
    void screeningPassesWithPriorConsentAndPassingCriteria() {
        Patient patient = patient(1);
        Trial trial = trial();

        Screening screening = service.screen(
                1L, patient, trial, site(trial),
                consent(patient, procedureTime().minusHours(1)),
                procedureTime(),
                new AtomicCriterion("AGE", context -> true)
        );

        assertTrue(screening.eligible());
        assertTrue(screening.failedCriteria().isEmpty());
    }

    @Test
    void rejectsMissingConsent() {
        Patient patient = patient(1);
        Trial trial = trial();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.screen(
                        1L, patient, trial, site(trial),
                        null, procedureTime(),
                        new AtomicCriterion("AGE", context -> true)
                )
        );
    }

    @Test
    void rejectsConsentRecordedAfterProcedureTime() {
        Patient patient = patient(1);
        Trial trial = trial();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.screen(
                        1L, patient, trial, site(trial),
                        consent(patient, procedureTime().plusMinutes(1)),
                        procedureTime(),
                        new AtomicCriterion("AGE", context -> true)
                )
        );
    }

    @Test
    void allowsConsentRecordedBeforeProcedureTime() {
        Patient patient = patient(1);
        Trial trial = trial();

        Screening screening = service.screen(
                1L, patient, trial, site(trial),
                consent(patient, procedureTime().minusMinutes(1)),
                procedureTime(),
                new AtomicCriterion("AGE", context -> true)
        );

        assertTrue(screening.eligible());
    }

    @Test
    void rejectsConsentBelongingToAnotherPatient() {
        Patient patient = patient(1);
        Patient anotherPatient = patient(2);
        Trial trial = trial();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.screen(
                        1L, patient, trial, site(trial),
                        consent(anotherPatient, procedureTime().minusHours(1)),
                        procedureTime(),
                        new AtomicCriterion("AGE", context -> true)
                )
        );
    }

    @Test
    void recordsAllFailedCriteriaWhenScreeningFails() {
        Patient patient = patient(1);
        Trial trial = trial();

        Criterion criteria = new AndCriterion(List.of(
                new AtomicCriterion("AGE", context -> false),
                new AtomicCriterion("HBA1C", context -> false)
        ));

        Screening screening = service.screen(
                1L, patient, trial, site(trial),
                consent(patient, procedureTime().minusHours(1)),
                procedureTime(),
                criteria
        );

        assertFalse(screening.eligible());
        assertEquals(
                List.of("AGE", "HBA1C"),
                screening.failedCriteria()
        );
    }

    @Test
    void rejectsSiteBelongingToAnotherTrial() {
        Patient patient = patient(1);
        Trial trial = trial();
        Trial anotherTrial = new Trial(2L, "Other Trial", 50, 10);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.screen(
                        1L, patient, trial, site(anotherTrial),
                        consent(patient, procedureTime().minusHours(1)),
                        procedureTime(),
                        new AtomicCriterion("AGE", context -> true)
                )
        );
    }
}