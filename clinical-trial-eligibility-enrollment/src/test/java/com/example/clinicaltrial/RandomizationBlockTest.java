package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class RandomizationBlockTest {

    private Trial trial() {
        return new Trial(1L, "Study A", 100, 25);
    }

    private Site site() {
        return new Site(1L, "Site A", trial());
    }

    private Arm armA() {
        return new Arm(1L, "Arm A", trial(), 50);
    }

    private Arm armB() {
        return new Arm(2L, "Arm B", trial(), 50);
    }

    @Test
    void blockOfFourContainsTwoAssignmentsPerArm() {
        RandomizationBlock block = new RandomizationBlock(
                1L, site(), Sex.FEMALE, 4, armA(), armB()
        );

        int countA = 0;
        int countB = 0;

        for (int i = 0; i < 4; i++) {
            Arm assigned = block.nextAssignment();

            if (assigned.id().equals(1L)) {
                countA++;
            } else if (assigned.id().equals(2L)) {
                countB++;
            }
        }

        assertEquals(2, countA);
        assertEquals(2, countB);
        assertTrue(block.isExhausted());
    }

    @Test
    void allowsBalancedBlockSizeOfTwo() {
        RandomizationBlock block = new RandomizationBlock(
                1L, site(), Sex.MALE, 2, armA(), armB()
        );

        assertNotNull(block.nextAssignment());
        assertNotNull(block.nextAssignment());
        assertTrue(block.isExhausted());
    }

    @Test
    void rejectsOddBlockSize() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RandomizationBlock(
                        1L, site(), Sex.FEMALE, 3, armA(), armB()
                )
        );
    }

    @Test
    void rejectsExhaustedBlock() {
        RandomizationBlock block = new RandomizationBlock(
                1L, site(), Sex.FEMALE, 2, armA(), armB()
        );

        block.nextAssignment();
        block.nextAssignment();

        assertThrows(
                IllegalStateException.class,
                block::nextAssignment
        );
    }

    @Test
    void rejectsArmsFromDifferentTrials() {
        Trial otherTrial = new Trial(2L, "Study B", 50, 10);
        Arm otherArm = new Arm(3L, "Other Arm", otherTrial, 20);

        assertThrows(
                IllegalArgumentException.class,
                () -> new RandomizationBlock(
                        1L, site(), Sex.FEMALE, 4, armA(), otherArm
                )
        );
    }

    @Test
    void rejectsSameArmTwice() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RandomizationBlock(
                        1L, site(), Sex.FEMALE, 4, armA(), armA()
                )
        );
    }
}