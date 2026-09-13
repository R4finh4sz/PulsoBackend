package pulsoescolar_api;

import jakarta.validation.Validation;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import pulsoescolar_api.dto.classroom.CreateClassroomRequest;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class ClassroomIdentifierTests {
    @Test
    void acceptsOnlyOneUppercaseAsciiLetter() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (char letter = 'A'; letter <= 'Z'; letter++) {
                assertTrue(validator.validate(new CreateClassroomRequest("3 ano", String.valueOf(letter))).isEmpty());
            }
            for (String identifier : new String[] {null, "", " ", "a", "AB", "1", "Á", " A", "A "}) {
                assertFalse(validator.validate(new CreateClassroomRequest("3 ano", identifier)).isEmpty(),
                        "Should reject: " + identifier);
            }
        }
    }

    @Test
    void migratesExistingClassroomsAndEnforcesDatabaseConstraints() throws Exception {
        String url = "jdbc:h2:mem:identifier_migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("1").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("INSERT INTO classrooms(name) VALUES ('3 ano')");
            Flyway.configure().dataSource(url, "sa", "").load().migrate();
            try (var result = statement.executeQuery("SELECT name, identifier FROM classrooms")) {
                assertTrue(result.next());
                assertEquals("3 ano", result.getString("name"));
                assertEquals("A", result.getString("identifier"));
            }
            statement.execute("INSERT INTO classrooms(name, identifier) VALUES ('3 ano', 'B')");
            assertThrows(SQLException.class, () ->
                    statement.execute("INSERT INTO classrooms(name, identifier) VALUES ('3 ano', 'B')"));
            assertThrows(SQLException.class, () ->
                    statement.execute("INSERT INTO classrooms(name, identifier) VALUES ('4 ano', 'a')"));
            assertThrows(SQLException.class, () ->
                    statement.execute("INSERT INTO classrooms(name) VALUES ('4 ano')"));
        }
    }
}
