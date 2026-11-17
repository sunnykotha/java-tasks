package com.example.clinicaltrial;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class DatabaseEnrollmentService {

    private final ClinicalTrialDatabase database;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentService enrollmentService = new EnrollmentService();

    public DatabaseEnrollmentService(ClinicalTrialDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException("Database is required");
        }
        this.database = database;
        this.enrollmentRepository = new EnrollmentRepository(database);
    }

    public Enrollment enroll(
            Long enrollmentId,
            Screening screening,
            Arm arm,
            LocalDateTime enrolledAt,
            Set<Long> conflictingTrialIds
    ) {
        if (enrollmentId == null || screening == null || arm == null
                || enrolledAt == null || conflictingTrialIds == null) {
            throw new IllegalArgumentException(
                    "Enrollment ID, screening, arm, timestamp, and conflict IDs are required"
            );
        }

        return database.inTransaction(connection -> {
            try {
                Trial trial = screening.trial();
                Site site = screening.site();

                if (!arm.trial().id().equals(trial.id())) {
                    throw new IllegalArgumentException(
                            "Arm must belong to the screening trial"
                    );
                }

                lockTrial(connection, trial.id());
                lockSite(connection, site.id(), trial.id());
                lockArm(connection, arm.id(), trial.id());

                if (isClosed(connection, "site_capacity", "site_id", site.id())) {
                    throw new IllegalStateException("Site is closed");
                }

                if (isClosed(connection, "arm_capacity", "arm_id", arm.id())) {
                    throw new IllegalStateException("Arm is closed");
                }

                long trialCount = countActive(
                        connection, "trial_id", trial.id(), null, null
                );
                long siteCount = countActive(
                        connection, "trial_id", trial.id(),
                        "site_id", site.id()
                );
                long armCount = countActive(
                        connection, "arm_id", arm.id(), null, null
                );

                if (trialCount >= trial.maxEnrollment()) {
                    throw new IllegalStateException(
                            "Overall trial enrollment cap reached"
                    );
                }

                if (siteCount >= trial.maxEnrollmentPerSite()) {
                    closeSite(connection, site.id());
                    throw new IllegalStateException("Site enrollment cap reached");
                }

                if (armCount >= arm.maxEnrollment()) {
                    closeArm(connection, arm.id());
                    throw new IllegalStateException("Arm enrollment cap reached");
                }

                rejectConflictingEnrollment(
                        connection, screening.patient().id(), conflictingTrialIds
                );

                Enrollment enrollment = enrollmentService.enroll(
                        enrollmentId,
                        screening,
                        arm,
                        enrolledAt,
                        List.of(),
                        conflictingTrialIds
                );

                enrollmentRepository.save(connection, enrollment);

                if (siteCount + 1 >= trial.maxEnrollmentPerSite()) {
                    closeSite(connection, site.id());
                }

                if (armCount + 1 >= arm.maxEnrollment()) {
                    closeArm(connection, arm.id());
                }

                return enrollment;
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "Database enrollment operation failed", exception
                );
            }
        });
    }

    public Enrollment enrollRandomized(
            Long enrollmentId,
            Screening screening,
            Sex sex,
            Arm armA,
            Arm armB,
            LocalDateTime enrolledAt,
            Set<Long> conflictingTrialIds,
            DatabaseRandomizationRepository randomizationRepository
    ) {
        if (enrollmentId == null || screening == null || sex == null
                || armA == null || armB == null || enrolledAt == null
                || conflictingTrialIds == null
                || randomizationRepository == null) {
            throw new IllegalArgumentException(
                    "Enrollment details, randomization inputs, and repository are required"
            );
        }

        Trial trial = screening.trial();
        Site site = screening.site();

        if (trial == null || site == null
                || !site.trial().id().equals(trial.id())
                || !armA.trial().id().equals(trial.id())
                || !armB.trial().id().equals(trial.id())) {
            throw new IllegalArgumentException(
                    "Screening site and randomization arms must belong to the same trial"
            );
        }

        if (armA.id().equals(armB.id())) {
            throw new IllegalArgumentException(
                    "Randomization arms must be different"
            );
        }

        if (!screening.eligible()) {
            throw new IllegalArgumentException(
                    "Only eligible patients can be enrolled"
            );
        }

        return database.inTransaction(connection -> {
            try {
                lockTrial(connection, trial.id());
                lockSite(connection, site.id(), trial.id());

                if (isClosed(connection, "site_capacity", "site_id", site.id())) {
                    throw new IllegalStateException("Site is closed");
                }

                long trialCount = countActive(
                        connection, "trial_id", trial.id(), null, null
                );
                long siteCount = countActive(
                        connection, "trial_id", trial.id(),
                        "site_id", site.id()
                );

                if (trialCount >= trial.maxEnrollment()) {
                    throw new IllegalStateException(
                            "Overall trial enrollment cap reached"
                    );
                }

                if (siteCount >= trial.maxEnrollmentPerSite()) {
                    closeSite(connection, site.id());
                    throw new IllegalStateException(
                            "Site enrollment cap reached"
                    );
                }

                rejectConflictingEnrollment(
                        connection,
                        screening.patient().id(),
                        conflictingTrialIds
                );

                Arm assignedArm =
                        randomizationRepository.assignNextInTransaction(
                                connection,
                                site,
                                sex,
                                armA,
                                armB,
                                enrollmentId
                        );

                long armCount = countActive(
                        connection, "arm_id", assignedArm.id(), null, null
                );

                int armCap;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT max_enrollment
                        FROM arm_capacity
                        WHERE arm_id = ?
                        """)) {
                    statement.setLong(1, assignedArm.id());
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalStateException(
                                    "Arm capacity not registered"
                            );
                        }
                        armCap = result.getInt("max_enrollment");
                    }
                }

                if (armCount >= armCap) {
                    throw new IllegalStateException(
                            "Arm enrollment cap reached"
                    );
                }

                Enrollment enrollment = enrollmentService.enroll(
                        enrollmentId,
                        screening,
                        assignedArm,
                        enrolledAt,
                        List.of(),
                        conflictingTrialIds
                );

                enrollmentRepository.save(connection, enrollment);

                if (siteCount + 1 >= trial.maxEnrollmentPerSite()) {
                    closeSite(connection, site.id());
                }

                if (armCount + 1 >= armCap) {
                    closeArm(connection, assignedArm.id());
                }

                return enrollment;
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "Database randomized enrollment failed", exception
                );
            }
        });
    }
    public void withdraw(Long enrollmentId, LocalDateTime withdrawnAt) {
        if (enrollmentId == null || withdrawnAt == null) {
            throw new IllegalArgumentException(
                    "Enrollment ID and withdrawal timestamp are required"
            );
        }

        database.inTransaction(connection -> {
            try {
                Long trialId;
                Long siteId;
                Long armId;

                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT trial_id, site_id, arm_id
                        FROM trial_enrollment
                        WHERE enrollment_id = ?
                        """)) {
                    statement.setLong(1, enrollmentId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException(
                                    "Enrollment not found"
                            );
                        }
                        trialId = result.getLong("trial_id");
                        siteId = result.getLong("site_id");
                        armId = result.getLong("arm_id");
                    }
                }

                lockTrial(connection, trialId);
                lockSite(connection, siteId, trialId);
                lockArm(connection, armId, trialId);

                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE trial_enrollment
                        SET status = 'WITHDRAWN', withdrawn_at = ?
                        WHERE enrollment_id = ? AND status = 'ACTIVE'
                        """)) {
                    statement.setTimestamp(1, Timestamp.valueOf(withdrawnAt));
                    statement.setLong(2, enrollmentId);

                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "Active enrollment not found"
                        );
                    }
                }

                long activeAtSite = countActive(
                        connection, "trial_id", trialId, "site_id", siteId
                );
                long activeInArm = countActive(
                        connection, "arm_id", armId, null, null
                );

                int siteCap;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT max_enrollment_per_site
                        FROM trial_capacity WHERE trial_id = ?
                        """)) {
                    statement.setLong(1, trialId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalStateException(
                                    "Trial capacity not registered"
                            );
                        }
                        siteCap = result.getInt("max_enrollment_per_site");
                    }
                }

                int armCap;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT max_enrollment
                        FROM arm_capacity WHERE arm_id = ?
                        """)) {
                    statement.setLong(1, armId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalStateException(
                                    "Arm capacity not registered"
                            );
                        }
                        armCap = result.getInt("max_enrollment");
                    }
                }

                if (activeAtSite < siteCap) {
                    reopenSite(connection, siteId);
                }

                if (activeInArm < armCap) {
                    reopenArm(connection, armId);
                }

                return null;
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "Could not withdraw enrollment", exception
                );
            }
        });
    }

    private void reopenSite(Connection connection, Long siteId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE site_capacity SET closed = FALSE WHERE site_id = ?"
        )) {
            statement.setLong(1, siteId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Site capacity not found");
            }
        }
    }

    private void reopenArm(Connection connection, Long armId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE arm_capacity SET closed = FALSE WHERE arm_id = ?"
        )) {
            statement.setLong(1, armId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Arm capacity not found");
            }
        }
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
                "SELECT trial_id FROM site_capacity WHERE site_id = ? FOR UPDATE"
        )) {
            statement.setLong(1, siteId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()
                        || result.getLong("trial_id") != trialId) {
                    throw new IllegalStateException(
                            "Site capacity is missing or belongs to another trial"
                    );
                }
            }
        }
    }

    private void lockArm(Connection connection, Long armId, Long trialId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT trial_id FROM arm_capacity WHERE arm_id = ? FOR UPDATE"
        )) {
            statement.setLong(1, armId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()
                        || result.getLong("trial_id") != trialId) {
                    throw new IllegalStateException(
                            "Arm capacity is missing or belongs to another trial"
                    );
                }
            }
        }
    }

    private boolean isClosed(
            Connection connection, String table, String idColumn, Long id
    ) throws SQLException {
        String sql = "SELECT closed FROM " + table
                + " WHERE " + idColumn + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException(
                            "Capacity record not found in " + table
                    );
                }
                return result.getBoolean("closed");
            }
        }
    }

    private long countActive(
            Connection connection,
            String firstColumn,
            Long firstId,
            String secondColumn,
            Long secondId
    ) throws SQLException {
        String sql = "SELECT COUNT(*) FROM trial_enrollment WHERE status = 'ACTIVE'"
                + " AND " + firstColumn + " = ?";

        if (secondColumn != null) {
            sql += " AND " + secondColumn + " = ?";
        }

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, firstId);
            if (secondColumn != null) {
                statement.setLong(2, secondId);
            }
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private void rejectConflictingEnrollment(
            Connection connection,
            Long patientId,
            Set<Long> conflictingTrialIds
    ) throws SQLException {
        if (conflictingTrialIds.isEmpty()) {
            return;
        }

        String placeholders = String.join(
                ",", java.util.Collections.nCopies(
                        conflictingTrialIds.size(), "?"
                )
        );

        String sql = "SELECT 1 FROM trial_enrollment"
                + " WHERE patient_id = ? AND status = 'ACTIVE'"
                + " AND trial_id IN (" + placeholders + ")";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, patientId);
            int index = 2;
            for (Long trialId : conflictingTrialIds) {
                if (trialId == null) {
                    throw new IllegalArgumentException(
                            "Conflicting trial IDs must not contain null"
                    );
                }
                statement.setLong(index++, trialId);
            }

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    throw new IllegalStateException(
                            "Patient is enrolled in a conflicting trial"
                    );
                }
            }
        }
    }

    private void closeSite(Connection connection, Long siteId)
            throws SQLException {
        updateClosed(connection, "site_capacity", "site_id", siteId);
    }

    private void closeArm(Connection connection, Long armId)
            throws SQLException {
        updateClosed(connection, "arm_capacity", "arm_id", armId);
    }

    private void updateClosed(
            Connection connection, String table, String idColumn, Long id
    ) throws SQLException {
        String sql = "UPDATE " + table + " SET closed = TRUE WHERE "
                + idColumn + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException(
                        "Could not close capacity record in " + table
                );
            }
        }
    }
}
