package com.example.clinicaltrial;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class ConsentMustPrecedeScreeningTest {

    @Test
    void rejectsConsentRecordedAtTheSameTimeAsTheProcedure() {
        Trial trial = new Trial(1L, "Trial", 10, 10);
        Site site = new Site(2L, "Site", trial);
        Patient patient = new Patient(
                3L, LocalDate.of(1990, 1, 1), Sex.FEMALE
        );

        LocalDateTime procedureAt =
                LocalDateTime.of(2026, 10, 27, 10, 0);

        Consent consent = new Consent(4L, patient, procedureAt);

        ScreeningService service = new ScreeningService();

        assertThrows(IllegalArgumentException.class, () ->
                service.screen(
                        5L,
                        patient,
                        trial,
                        site,
                        consent,
                        procedureAt,
                        new AtomicCriterion("eligible", context -> true)
                )
        );
    }
}