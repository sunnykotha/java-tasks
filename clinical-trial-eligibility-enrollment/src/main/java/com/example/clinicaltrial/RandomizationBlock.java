package com.example.clinicaltrial;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RandomizationBlock {

    private final Long id;
    private final Site site;
    private final Sex sex;
    private final List<Arm> assignments;
    private int nextIndex;

    public RandomizationBlock(
            Long id,
            Site site,
            Sex sex,
            int blockSize,
            Arm armA,
            Arm armB
    ) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "Block ID must be positive"
            );
        }

        if (site == null || sex == null || armA == null || armB == null) {
            throw new IllegalArgumentException(
                    "Site, sex, and both arms are required"
            );
        }

        if (blockSize < 2 || blockSize % 2 != 0) {
            throw new IllegalArgumentException(
                    "Block size must be an even number of at least two"
            );
        }

        if (armA.id().equals(armB.id())) {
            throw new IllegalArgumentException(
                    "Randomization arms must be different"
            );
        }

        if (!site.trial().id().equals(armA.trial().id())
                || !site.trial().id().equals(armB.trial().id())) {
            throw new IllegalArgumentException(
                    "Both arms and the site must belong to the same trial"
            );
        }

        this.id = id;
        this.site = site;
        this.sex = sex;

        List<Arm> generated = new ArrayList<>();

        for (int i = 0; i < blockSize / 2; i++) {
            generated.add(armA);
            generated.add(armB);
        }

        Collections.shuffle(generated, new SecureRandom());
        this.assignments = List.copyOf(generated);
        this.nextIndex = 0;
    }

    public Long id() {
        return id;
    }

    public Site site() {
        return site;
    }

    public Sex sex() {
        return sex;
    }

    public synchronized Arm nextAssignment() {
        if (nextIndex >= assignments.size()) {
            throw new IllegalStateException(
                    "Randomization block is exhausted"
            );
        }

        return assignments.get(nextIndex++);
    }

    public synchronized boolean isExhausted() {
        return nextIndex >= assignments.size();
    }
}