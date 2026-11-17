package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class LabResultTest {

    private Patient patient() {
        return new Patient(
                1L,
                LocalDate.of(1990, 5, 15),
                Sex.FEMALE
        );
    }

    private LabResult validResult() {
        return new LabResult(
                1L,
                patient(),
                "HbA1c",
                new BigDecimal("8.0"),
                "%",
                LocalDate.of(2026, 1, 1)
        );
    }

    @Test
    void createsLabResultWithValidDetails() {
        LabResult result = validResult();

        assertEquals("HbA1c", result.testName());
        assertEquals(new BigDecimal("8.0"), result.value());
        assertEquals("%", result.unit());
        assertEquals(LocalDate.of(2026, 1, 1), result.collectedAt());
    }

    @Test
    void rejectsNonPositiveId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LabResult(
                        0L, patient(), "HbA1c",
                        BigDecimal.ONE, "%",
                        LocalDate.of(2026, 1, 1)
                )
        );
    }

    @Test
    void rejectsNullPatient() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LabResult(
                        1L, null, "HbA1c",
                        BigDecimal.ONE, "%",
                        LocalDate.of(2026, 1, 1)
                )
        );
    }

    @Test
    void rejectsBlankTestName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LabResult(
                        1L, patient(), " ",
                        BigDecimal.ONE, "%",
                        LocalDate.of(2026, 1, 1)
                )
        );
    }

    @Test
    void rejectsNullValue() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LabResult(
                        1L, patient(), "HbA1c",
                        null, "%",
                        LocalDate.of(2026, 1, 1)
                )
        );
    }

    @Test
    void rejectsBlankUnit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LabResult(
                        1L, patient(), "HbA1c",
                        BigDecimal.ONE, " ",
                        LocalDate.of(2026, 1, 1)
                )
        );
    }

    @Test
    void rejectsNullCollectionDate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LabResult(
                        1L, patient(), "HbA1c",
                        BigDecimal.ONE, "%", null
                )
        );
    }
}