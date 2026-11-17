package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArmTest {

    private Trial validTrial() {
        return new Trial(1L, "Diabetes Study", 100, 25);
    }

    @Test
    void createsArmWithValidDetails() {
        Trial trial = validTrial();

        Arm arm = new Arm(1L, "Treatment A", trial, 50);

        assertEquals(1L, arm.id());
        assertEquals("Treatment A", arm.name());
        assertEquals(trial, arm.trial());
        assertEquals(50, arm.maxEnrollment());
    }

    @Test
    void rejectsNullArmId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Arm(null, "Treatment A", validTrial(), 50)
        );
    }

    @Test
    void rejectsBlankArmName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Arm(1L, " ", validTrial(), 50)
        );
    }

    @Test
    void rejectsNullTrial() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Arm(1L, "Treatment A", null, 50)
        );
    }

    @Test
    void rejectsNonPositiveArmEnrollmentLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Arm(1L, "Treatment A", validTrial(), 0)
        );
    }
}