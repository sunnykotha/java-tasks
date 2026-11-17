package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RandomizationClosureTest {

    @Test
    void stopsBalancedRandomizationWhenAnArmClosesMidBlock() {
        Trial trial = new Trial(1L, "Trial", 20, 20);
        Site site = new Site(1L, "Site A", trial);
        Arm armA = new Arm(1L, "Arm A", trial, 10);
        Arm armB = new Arm(2L, "Arm B", trial, 10);

        EnrollmentCapacityService capacity = new EnrollmentCapacityService();
        RandomizationService randomization = new RandomizationService(4);

        randomization.assignNext(site, Sex.FEMALE, armA, armB);
        capacity.closeArm(armA.id());

        assertThrows(
                IllegalStateException.class,
                () -> randomization.assignNextAvailable(
                        site, Sex.FEMALE, armA, armB, capacity
                )
        );
    }

    @Test
    void rejectsAssignmentWhenBothArmsAreClosed() {
        Trial trial = new Trial(1L, "Trial", 20, 20);
        Site site = new Site(1L, "Site A", trial);
        Arm armA = new Arm(1L, "Arm A", trial, 10);
        Arm armB = new Arm(2L, "Arm B", trial, 10);

        EnrollmentCapacityService capacity = new EnrollmentCapacityService();
        RandomizationService randomization = new RandomizationService(4);

        capacity.closeArm(armA.id());
        capacity.closeArm(armB.id());

        assertThrows(
                IllegalStateException.class,
                () -> randomization.assignNextAvailable(
                        site, Sex.FEMALE, armA, armB, capacity
                )
        );
    }
}