package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RandomizationServiceTest {

    private Trial trial() {
        return new Trial(1L, "Study A", 100, 25);
    }

    private Site site(long id, String name) {
        return new Site(id, name, trial());
    }

    private Arm armA() {
        return new Arm(1L, "Arm A", trial(), 50);
    }

    private Arm armB() {
        return new Arm(2L, "Arm B", trial(), 50);
    }

    @Test
    void assignsTwoPatientsToEachArmInCompleteBlock() {
        RandomizationService service = new RandomizationService(4);
        List<Arm> results = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            results.add(service.assignNext(
                    site(1L, "Site A"), Sex.FEMALE, armA(), armB()
            ));
        }

        assertEquals(
                2,
                results.stream()
                        .filter(arm -> arm.id().equals(1L))
                        .count()
        );

        assertEquals(
                2,
                results.stream()
                        .filter(arm -> arm.id().equals(2L))
                        .count()
        );
    }

    @Test
    void createsNewBlockAfterPreviousBlockIsExhausted() {
        RandomizationService service = new RandomizationService(4);

        for (int i = 0; i < 4; i++) {
            service.assignNext(
                    site(1L, "Site A"), Sex.FEMALE, armA(), armB()
            );
        }

        List<Arm> nextBlock = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            nextBlock.add(service.assignNext(
                    site(1L, "Site A"), Sex.FEMALE, armA(), armB()
            ));
        }

        assertEquals(
                2,
                nextBlock.stream()
                        .filter(arm -> arm.id().equals(1L))
                        .count()
        );

        assertEquals(
                2,
                nextBlock.stream()
                        .filter(arm -> arm.id().equals(2L))
                        .count()
        );
    }

    @Test
    void keepsSeparateBlocksForDifferentSexStrata() {
        RandomizationService service = new RandomizationService(4);

        List<Arm> female = new ArrayList<>();
        List<Arm> male = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            female.add(service.assignNext(
                    site(1L, "Site A"), Sex.FEMALE, armA(), armB()
            ));
            male.add(service.assignNext(
                    site(1L, "Site A"), Sex.MALE, armA(), armB()
            ));
        }

        assertEquals(2, female.size());
        assertEquals(2, male.size());
    }

    @Test
    void keepsSeparateBlocksForDifferentSites() {
        RandomizationService service = new RandomizationService(4);

        for (int i = 0; i < 4; i++) {
            service.assignNext(
                    site(1L, "Site A"), Sex.FEMALE, armA(), armB()
            );
        }

        List<Arm> siteB = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            siteB.add(service.assignNext(
                    site(2L, "Site B"), Sex.FEMALE, armA(), armB()
            ));
        }

        assertEquals(
                2,
                siteB.stream()
                        .filter(arm -> arm.id().equals(1L))
                        .count()
        );

        assertEquals(
                2,
                siteB.stream()
                        .filter(arm -> arm.id().equals(2L))
                        .count()
        );
    }

    @Test
    void rejectsInvalidBlockSize() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RandomizationService(3)
        );
    }
}