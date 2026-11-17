package com.example.clinicaltrial;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

public class DatabaseCapacityRepository {

    private final ClinicalTrialDatabase database;

    public DatabaseCapacityRepository(ClinicalTrialDatabase database) {
        this.database = Objects.requireNonNull(database, "database must not be null");
    }

    public void registerTrial(Trial trial) {
        Objects.requireNonNull(trial, "trial must not be null");

        database.inTransaction(connection -> {
            executeInsert(connection,
                    "INSERT INTO trial_capacity (trial_id, max_enrollment, max_enrollment_per_site) VALUES (?, ?, ?)",
                    statement -> {
                        statement.setLong(1, trial.id());
                        statement.setInt(2, trial.maxEnrollment());
                        statement.setInt(3, trial.maxEnrollmentPerSite());
                    });
            return null;
        });
    }

    public void registerSite(Site site) {
        Objects.requireNonNull(site, "site must not be null");

        database.inTransaction(connection -> {
            executeInsert(connection,
                    "INSERT INTO site_capacity (site_id, trial_id, closed) VALUES (?, ?, FALSE)",
                    statement -> {
                        statement.setLong(1, site.id());
                        statement.setLong(2, site.trial().id());
                    });
            return null;
        });
    }

    public void registerArm(Arm arm) {
        Objects.requireNonNull(arm, "arm must not be null");

        database.inTransaction(connection -> {
            executeInsert(connection,
                    "INSERT INTO arm_capacity (arm_id, trial_id, max_enrollment, closed) VALUES (?, ?, ?, FALSE)",
                    statement -> {
                        statement.setLong(1, arm.id());
                        statement.setLong(2, arm.trial().id());
                        statement.setInt(3, arm.maxEnrollment());
                    });
            return null;
        });
    }

    private void executeInsert(
            Connection connection,
            String sql,
            StatementBinder binder) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to register enrollment capacity", exception);
        }
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}