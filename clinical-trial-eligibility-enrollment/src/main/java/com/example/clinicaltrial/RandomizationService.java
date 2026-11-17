package com.example.clinicaltrial;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class RandomizationService {

    private record Stratum(Long siteId, Sex sex) {
    }

    private final int blockSize;
    private final Map<Stratum, RandomizationBlock> currentBlocks =
            new HashMap<>();
    private final AtomicLong nextBlockId = new AtomicLong(1);

    public RandomizationService(int blockSize) {
        if (blockSize < 2 || blockSize % 2 != 0) {
            throw new IllegalArgumentException(
                    "Block size must be an even number of at least two"
            );
        }

        this.blockSize = blockSize;
    }

    public synchronized Arm assignNext(
            Site site,
            Sex sex,
            Arm armA,
            Arm armB
    ) {
        if (site == null || sex == null || armA == null || armB == null) {
            throw new IllegalArgumentException(
                    "Site, sex, and both arms are required"
            );
        }

        if (!site.trial().id().equals(armA.trial().id())
                || !site.trial().id().equals(armB.trial().id())) {
            throw new IllegalArgumentException(
                    "Site and arms must belong to the same trial"
            );
        }

        Stratum stratum = new Stratum(site.id(), sex);
        RandomizationBlock block = currentBlocks.get(stratum);

        if (block == null || block.isExhausted()) {
            block = new RandomizationBlock(
                    nextBlockId.getAndIncrement(),
                    site,
                    sex,
                    blockSize,
                    armA,
                    armB
            );
            currentBlocks.put(stratum, block);
        }

        return block.nextAssignment();
    }

    public synchronized Arm assignNextAvailable(
            Site site,
            Sex sex,
            Arm armA,
            Arm armB,
            EnrollmentCapacityService capacityService
    ) {
        if (site == null || sex == null || armA == null
                || armB == null || capacityService == null) {
            throw new IllegalArgumentException(
                    "Site, sex, arms, and capacity service must not be null"
            );
        }

        if (!site.trial().id().equals(armA.trial().id())
                || !site.trial().id().equals(armB.trial().id())) {
            throw new IllegalArgumentException(
                    "Site and both arms must belong to the same trial"
            );
        }

        boolean armAClosed = capacityService.isArmClosed(armA.id());
        boolean armBClosed = capacityService.isArmClosed(armB.id());

        if (armAClosed && armBClosed) {
            throw new IllegalStateException(
                    "Both randomization arms are closed"
            );
        }

        if (armAClosed || armBClosed) {
            throw new IllegalStateException(
                    "Balanced randomization cannot continue because one arm is closed"
            );
        }

        return assignNext(site, sex, armA, armB);
    }
}