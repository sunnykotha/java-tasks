package com.example.clinicaltrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClinicalTrialDatabaseTransactionTest {

    private ClinicalTrialDatabase database;

    @BeforeEach
    void setUp() throws SQLException {
        database = new ClinicalTrialDatabase(
                "jdbc:h2:mem:transaction_test;DB_CLOSE_DELAY=-1");

        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS transaction_test");
            statement.execute("""
                    CREATE TABLE transaction_test (
                        id INT PRIMARY KEY,
                        test_value VARCHAR(50) NOT NULL
                    )
                    """);
        }
    }

    @Test
    void commitsSuccessfulTransaction() throws SQLException {
        database.inTransaction(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "INSERT INTO transaction_test (id, test_value) VALUES (1, 'committed')");
                return null;
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        });

        assertEquals(1, countRows());
    }

    @Test
    void rollsBackTransactionWhenOperationFails() throws SQLException {
        assertThrows(IllegalArgumentException.class, () ->
                database.inTransaction(connection -> {
                    try (Statement statement = connection.createStatement()) {
                        statement.executeUpdate(
                                "INSERT INTO transaction_test (id, test_value) VALUES (2, 'rolled back')");
                    } catch (SQLException exception) {
                        throw new IllegalStateException(exception);
                    }

                    throw new IllegalArgumentException("Force rollback");
                }));

        assertEquals(0, countRows());
    }

    private int countRows() throws SQLException {
        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT COUNT(*) FROM transaction_test")) {
            result.next();
            return result.getInt(1);
        }
    }
}