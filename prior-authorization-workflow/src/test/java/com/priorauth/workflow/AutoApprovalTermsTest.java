package com.priorauth.workflow;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AutoApprovalTermsTest {
    @Test
    void rejectsInvalidValidityWindow() {
        Instant effective = Instant.parse("2026-10-20T00:00:00Z");
        assertThrows(IllegalArgumentException.class,
                () -> new AutoApprovalTerms(effective, effective, 2));
    }

    @Test
    void rejectsNonPositiveApprovedUnits() {
        assertThrows(IllegalArgumentException.class,
                () -> new AutoApprovalTerms(
                        Instant.parse("2026-10-20T00:00:00Z"),
                        Instant.parse("2027-10-20T00:00:00Z"), 0));
    }
}
