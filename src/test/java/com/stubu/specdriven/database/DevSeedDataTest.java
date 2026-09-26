package com.stubu.specdriven.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * The sample data of the "dev" profile (db/dev/R__dev_seed_data.sql) still fits the database schema: every migration
 * that adds a required column must be reflected in it, or the application no longer starts in development.
 */
class DevSeedDataTest {

    private static final String URL = "jdbc:h2:mem:dev-seed-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
            + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";

    @Test
    void theSampleDataLoadsAndPutsEveryEmployeeInARegion() throws SQLException {
        Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration", "classpath:db/dev").load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(URL, "sa", "")) {
            assertEquals(5, count(connection, "select count(*) from employee"));
            assertEquals(0, count(connection, "select count(*) from employee where region_id is null"));
            assertEquals("USA", string(connection, "select r.name from employee e join region r on r.id = e.region_id "
                    + "where e.email = 'erik.employee@example.com'"), "Erik works in another region than his manager Bob");
            assertEquals("Default", string(connection, "select r.name from employee e join region r on r.id = e.region_id "
                    + "where e.email = 'bob.manager@example.com'"));
            assertTrue(count(connection, "select count(*) from public_holiday") >= 2, "Holidays of two regions");
        }
    }

    private static long count(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static String string(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }
}
