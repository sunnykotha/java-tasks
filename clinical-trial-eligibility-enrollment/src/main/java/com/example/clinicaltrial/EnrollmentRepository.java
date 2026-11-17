package com.example.clinicaltrial;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public final class EnrollmentRepository {

    private final ClinicalTrialDatabase database;

    public EnrollmentRepository(ClinicalTrialDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException("Database is required");
        }
        this.database = database;
    }

    public void save(Enrollment enrollment) {
        try (Connection connection = database.getConnection()) {
            save(connection, enrollment);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not save enrollment", exception);
        }
    }

    public void save(Connection connection, Enrollment enrollment)
            throws SQLException {
        if (connection == null || enrollment == null) {
            throw new IllegalArgumentException(
                "Connection and enrollment are required"
            );
        }

        String sql = """
            INSERT INTO trial_enrollment
                (enrollment_id, patient_id, trial_id, site_id, arm_id,
                 enrolled_at, status, withdrawn_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, enrollment.id());
            statement.setLong(2, enrollment.patient().id());
            statement.setLong(3, enrollment.trial().id());
            statement.setLong(4, enrollment.site().id());
            statement.setLong(5, enrollment.arm().id());
            statement.setTimestamp(6, Timestamp.valueOf(enrollment.enrolledAt()));
            statement.setString(7, enrollment.status().name());

            if (enrollment.withdrawnAt() == null) {
                statement.setTimestamp(8, null);
            } else {
                statement.setTimestamp(
                    8, Timestamp.valueOf(enrollment.withdrawnAt())
                );
            }

            statement.executeUpdate();
        }
    }

    public boolean exists(Long enrollmentId) {
        if (enrollmentId == null) {
            throw new IllegalArgumentException("Enrollment ID is required");
        }

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT 1 FROM trial_enrollment WHERE enrollment_id = ?"
             )) {
            statement.setLong(1, enrollmentId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Could not query enrollment", exception
            );
        }
    }

    public void updateWithdrawal(Long enrollmentId, LocalDateTime withdrawnAt) {
        if (enrollmentId == null || withdrawnAt == null) {
            throw new IllegalArgumentException(
                "Enrollment ID and withdrawal timestamp are required"
            );
        }

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 UPDATE trial_enrollment
                 SET status = 'WITHDRAWN', withdrawn_at = ?
                 WHERE enrollment_id = ? AND status = 'ACTIVE'
                 """)) {

            statement.setTimestamp(1, Timestamp.valueOf(withdrawnAt));
            statement.setLong(2, enrollmentId);

            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException(
                    "Active enrollment not found"
                );
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Could not update enrollment withdrawal", exception
            );
        }
    }
}