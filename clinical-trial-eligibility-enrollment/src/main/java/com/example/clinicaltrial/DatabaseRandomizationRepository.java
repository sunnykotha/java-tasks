package com.example.clinicaltrial;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Objects;

public final class DatabaseRandomizationRepository {

    private final ClinicalTrialDatabase database;
    private final int blockSize;

    public DatabaseRandomizationRepository(
            ClinicalTrialDatabase database, int blockSize
    ) {
        this.database = Objects.requireNonNull(database, "database is required");

        if (blockSize < 2 || blockSize % 2 != 0) {
            throw new IllegalArgumentException(
                    "Block size must be an even number of at least two"
            );
        }

        this.blockSize = blockSize;
    }

    public Arm assignNext(Site site, Sex sex, Arm armA, Arm armB) {
        if (site == null || sex == null || armA == null || armB == null) {
            throw new IllegalArgumentException(
                    "Site, sex, and both arms are required"
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
                    "Site and arms must belong to the same trial"
            );
        }

        return database.inTransaction(connection -> {
            try {
                lockTrial(connection, site.trial().id());
                lockSite(connection, site.id(), site.trial().id());
                ensureArmRegistered(connection, armA.id(), site.trial().id());
                ensureArmRegistered(connection, armB.id(), site.trial().id());

                Long blockId = findAvailableBlock(connection, site.id(), sex);

                if (blockId == null) {
                    blockId = createBlock(connection, site, sex, armA, armB);
                }

                return consumeNext(connection, blockId, armA, armB, null);
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "Could not assign database randomization", exception
                );
            }
        });
    }

    Arm assignNextInTransaction(
            Connection connection,
            Site site,
            Sex sex,
            Arm armA,
            Arm armB,
            Long enrollmentId
    ) throws SQLException {
        lockTrial(connection, site.trial().id());
        lockSite(connection, site.id(), site.trial().id());
        ensureArmRegistered(connection, armA.id(), site.trial().id());
        ensureArmRegistered(connection, armB.id(), site.trial().id());

        Long blockId = findAvailableBlock(connection, site.id(), sex);
        if (blockId == null) {
            blockId = createBlock(connection, site, sex, armA, armB);
        }

        return consumeNext(connection, blockId, armA, armB, enrollmentId);
    }
    private void lockTrial(Connection connection, Long trialId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT trial_id FROM trial_capacity WHERE trial_id = ? FOR UPDATE"
        )) {
            statement.setLong(1, trialId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException(
                            "Trial capacity has not been registered"
                    );
                }
            }
        }
    }

    private void lockSite(Connection connection, Long siteId, Long trialId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT trial_id, closed FROM site_capacity WHERE site_id = ? FOR UPDATE"
        )) {
            statement.setLong(1, siteId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getLong("trial_id") != trialId) {
                    throw new IllegalStateException(
                            "Site capacity is missing or belongs to another trial"
                    );
                }
                if (result.getBoolean("closed")) {
                    throw new IllegalStateException("Site is closed");
                }
            }
        }
    }

    private void ensureArmRegistered(
            Connection connection, Long armId, Long trialId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT trial_id, closed FROM arm_capacity WHERE arm_id = ? FOR UPDATE"
        )) {
            statement.setLong(1, armId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getLong("trial_id") != trialId) {
                    throw new IllegalStateException(
                            "Arm capacity is missing or belongs to another trial"
                    );
                }
                if (result.getBoolean("closed")) {
                    throw new IllegalStateException("Arm is closed");
                }
            }
        }
    }

    private Long findAvailableBlock(
            Connection connection, Long siteId, Sex sex
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT block_id
                FROM randomization_block
                WHERE site_id = ? AND sex = ? AND next_slot < block_size
                ORDER BY block_id
                FETCH FIRST 1 ROW ONLY
                FOR UPDATE
                """)) {
            statement.setLong(1, siteId);
            statement.setString(2, sex.name());

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong("block_id") : null;
            }
        }
    }

    private Long createBlock(
            Connection connection,
            Site site,
            Sex sex,
            Arm armA,
            Arm armB
    ) throws SQLException {
        long blockId;

        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO randomization_block
                    (site_id, sex, block_size, arm_a_id, arm_b_id, next_slot)
                VALUES (?, ?, ?, ?, ?, 0)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, site.id());
            statement.setString(2, sex.name());
            statement.setInt(3, blockSize);
            statement.setLong(4, armA.id());
            statement.setLong(5, armB.id());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException(
                            "Database did not return a randomization block ID"
                    );
                }
                blockId = keys.getLong(1);
            }
        }

        RandomizationBlock generated = new RandomizationBlock(
                blockId, site, sex, blockSize, armA, armB
        );

        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO randomization_assignment
                    (block_id, slot_index, arm_id)
                VALUES (?, ?, ?)
                """)) {
            for (int slot = 0; slot < blockSize; slot++) {
                Arm assignment = generated.nextAssignment();
                statement.setLong(1, blockId);
                statement.setInt(2, slot);
                statement.setLong(3, assignment.id());
                statement.addBatch();
            }
            statement.executeBatch();
        }

        return blockId;
    }

    private Arm consumeNext(
            Connection connection, Long blockId, Arm armA, Arm armB, Long enrollmentId
    ) throws SQLException {
        int slot;

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT next_slot
                FROM randomization_block
                WHERE block_id = ? AND next_slot < block_size
                FOR UPDATE
                """)) {
            statement.setLong(1, blockId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException(
                            "Randomization block is exhausted"
                    );
                }
                slot = result.getInt("next_slot");
            }
        }

        Long assignedArmId;

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT arm_id
                FROM randomization_assignment
                WHERE block_id = ? AND slot_index = ?
                  AND consumed_at IS NULL
                FOR UPDATE
                """)) {
            statement.setLong(1, blockId);
            statement.setInt(2, slot);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException(
                            "Randomization assignment is already consumed or missing"
                    );
                }
                assignedArmId = result.getLong("arm_id");
            }
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE randomization_assignment
                SET consumed_at = ?, consumed_by_enrollment_id = ?
                WHERE block_id = ? AND slot_index = ?
                  AND consumed_at IS NULL
                """)) {
            statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            if (enrollmentId == null) {
                statement.setNull(2, java.sql.Types.BIGINT);
            } else {
                statement.setLong(2, enrollmentId);
            }
            statement.setLong(3, blockId);
            statement.setInt(4, slot);

            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException(
                        "Randomization assignment could not be consumed"
                );
            }
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE randomization_block
                SET next_slot = ?
                WHERE block_id = ? AND next_slot = ?
                """)) {
            statement.setInt(1, slot + 1);
            statement.setLong(2, blockId);
            statement.setInt(3, slot);

            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException(
                        "Randomization block position changed unexpectedly"
                );
            }
        }

        if (assignedArmId.equals(armA.id())) {
            return armA;
        }
        if (assignedArmId.equals(armB.id())) {
            return armB;
        }

        throw new IllegalStateException(
                "Stored assignment does not match the supplied arms"
        );
    }
}