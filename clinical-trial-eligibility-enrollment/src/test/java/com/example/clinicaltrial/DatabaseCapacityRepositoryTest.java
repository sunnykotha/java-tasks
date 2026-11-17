package com.example.clinicaltrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseCapacityRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void registersTrialSiteAndArmCapacity() throws Exception {
        String jdbcUrl = "jdbc:h2:file:" +
                tempDir.resolve("capacity_db").toAbsolutePath().toString().replace("\\", "/");

        ClinicalTrialDatabase database = new ClinicalTrialDatabase(jdbcUrl);
        DatabaseCapacityRepository repository = new DatabaseCapacityRepository(database);

        Trial trial = new Trial(101L, "Trial A", 20, 10);
        Site site = new Site(201L, "Site A", trial);
        Arm arm = new Arm(301L, "Arm A", trial, 8);

        repository.registerTrial(trial);
        repository.registerSite(site);
        repository.registerArm(arm);

        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery(
                    "SELECT max_enrollment, max_enrollment_per_site FROM trial_capacity WHERE trial_id = 101")) {
                assertNotNull(result);
                result.next();
                assertEquals(20, result.getInt(1));
                assertEquals(10, result.getInt(2));
            }

            try (ResultSet result = statement.executeQuery(
                    "SELECT trial_id, closed FROM site_capacity WHERE site_id = 201")) {
                result.next();
                assertEquals(101L, result.getLong(1));
                assertEquals(false, result.getBoolean(2));
            }

            try (ResultSet result = statement.executeQuery(
                    "SELECT trial_id, max_enrollment, closed FROM arm_capacity WHERE arm_id = 301")) {
                result.next();
                assertEquals(101L, result.getLong(1));
                assertEquals(8, result.getInt(2));
                assertEquals(false, result.getBoolean(3));
            }
        }
    }
}