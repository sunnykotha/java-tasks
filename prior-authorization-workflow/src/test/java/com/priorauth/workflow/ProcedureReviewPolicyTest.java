package com.priorauth.workflow;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProcedureReviewPolicyTest {
    @Test
    void onlyExplicitlyConfiguredCodesAreRoutine() {
        ProcedureReviewPolicy policy =
                new ProcedureReviewPolicy(Set.of("MRI-ROUTINE"));

        assertTrue(policy.isRoutine("mri-routine"));
        assertFalse(policy.isRoutine("COMPLEX-SURGERY"));
        assertFalse(policy.isRoutine(null));
    }

    @Test
    void rejectsBlankRoutineCode() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProcedureReviewPolicy(Set.of(" ")));
    }
}
