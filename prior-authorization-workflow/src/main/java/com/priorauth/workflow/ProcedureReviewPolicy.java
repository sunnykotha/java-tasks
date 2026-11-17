package com.priorauth.workflow;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Explicit classification of procedure codes that nurses may approve.
 * Codes not listed here are treated as non-routine and require medical-director
 * approval. The task does not prescribe a universal clinical code list, so the
 * application supplies this policy as configuration.
 */
public final class ProcedureReviewPolicy {
    private final Set<String> routineProcedureCodes;

    public ProcedureReviewPolicy(Set<String> routineProcedureCodes) {
        Objects.requireNonNull(routineProcedureCodes,
                "Routine procedure codes are required");
        this.routineProcedureCodes = routineProcedureCodes.stream()
                .map(code -> {
                    if (code == null || code.isBlank()) {
                        throw new IllegalArgumentException(
                                "Routine procedure codes must not be blank");
                    }
                    return code.trim().toUpperCase();
                })
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isRoutine(String procedureCode) {
        return procedureCode != null
                && routineProcedureCodes.contains(procedureCode.trim().toUpperCase());
    }

    public Set<String> getRoutineProcedureCodes() {
        return routineProcedureCodes;
    }
}
