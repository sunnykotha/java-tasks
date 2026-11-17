package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompositeCriterionTest {

    private EvaluationContext context() {
        Patient patient = new Patient(
                1L,
                LocalDate.of(1990, 5, 15),
                Sex.FEMALE
        );

        return new EvaluationContext(
                patient,
                LocalDate.of(2026, 1, 10)
        );
    }

    private Criterion pass(String id) {
        return new AtomicCriterion(id, c -> true);
    }

    private Criterion fail(String id) {
        return new AtomicCriterion(id, c -> false);
    }

    @Test
    void atomicCriterionPassesWhenConditionIsTrue() {
        assertTrue(pass("AGE").evaluate(context()).passed());
    }

    @Test
    void atomicCriterionReportsItsIdWhenConditionIsFalse() {
        EvaluationResult result = fail("AGE").evaluate(context());

        assertFalse(result.passed());
        assertEquals(List.of("AGE"), result.failedCriteria());
    }

    @Test
    void andPassesWhenAllChildrenPass() {
        Criterion criterion = new AndCriterion(
                List.of(pass("AGE"), pass("HBA1C"))
        );

        assertTrue(criterion.evaluate(context()).passed());
    }

    @Test
    void andReportsAllFailedChildren() {
        Criterion criterion = new AndCriterion(
                List.of(fail("AGE"), pass("HBA1C"), fail("CONSENT"))
        );

        EvaluationResult result = criterion.evaluate(context());

        assertFalse(result.passed());
        assertEquals(
                List.of("AGE", "CONSENT"),
                result.failedCriteria()
        );
    }

    @Test
    void orPassesWhenAtLeastOneChildPasses() {
        Criterion criterion = new OrCriterion(
                List.of(fail("A"), pass("B"))
        );

        assertTrue(criterion.evaluate(context()).passed());
    }

    @Test
    void orReportsEveryFailureWhenAllChildrenFail() {
        Criterion criterion = new OrCriterion(
                List.of(fail("A"), fail("B"))
        );

        EvaluationResult result = criterion.evaluate(context());

        assertFalse(result.passed());
        assertEquals(List.of("A", "B"), result.failedCriteria());
    }

    @Test
    void notPassesWhenChildFails() {
        Criterion criterion = new NotCriterion(fail("INSULIN"));

        assertTrue(criterion.evaluate(context()).passed());
    }

    @Test
    void notFailsWhenChildPasses() {
        Criterion criterion = new NotCriterion(pass("INSULIN"));

        assertFalse(criterion.evaluate(context()).passed());
    }

    @Test
    void evaluatesNestedBooleanTree() {
        Criterion criterion = new AndCriterion(List.of(
                pass("AGE_18_65"),
                pass("HBA1C_7_10"),
                new NotCriterion(fail("INSULIN_LAST_6_MONTHS"))
        ));

        assertTrue(criterion.evaluate(context()).passed());
    }

    @Test
    void rejectsEmptyAnd() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AndCriterion(List.of())
        );
    }

    @Test
    void rejectsEmptyOr() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OrCriterion(List.of())
        );
    }

    @Test
    void rejectsNullNotChild() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new NotCriterion(null)
        );
    }

    @Test
    void rejectsNullAtomicCondition() {
        assertThrows(
                NullPointerException.class,
                () -> new AtomicCriterion("AGE", null)
        );
    }
}