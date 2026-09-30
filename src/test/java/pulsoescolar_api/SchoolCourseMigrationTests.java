package pulsoescolar_api;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

class SchoolCourseMigrationTests {
    @Test
    void renamesExistingTablePreservingDataAndConstraints() throws Exception {
        String url = "jdbc:h2:mem:school_course_migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            var dataSource = new SingleConnectionDataSource(connection, true);
            Flyway.configure().dataSource(dataSource).target("16").load().migrate();
            statement.execute("INSERT INTO classrooms(id, name, identifier) VALUES (1, 'Class', 'A')");
            statement.execute("""
                    INSERT INTO school_users(id, full_name, ra, email, password_hash, role)
                    VALUES (1, 'Teacher', 'T1', 'teacher@example.com', 'unused', 'TEACHER')
                    """);
            statement.execute("INSERT INTO classroom_teachers VALUES (1, 1)");
            statement.execute("INSERT INTO subjects(name, classroom_id, teacher_id) VALUES ('Math', 1, 1)");
            long originalId;
            try (var result = statement.executeQuery("SELECT id FROM subjects")) {
                assertTrue(result.next());
                originalId = result.getLong(1);
            }

            Flyway.configure().dataSource(dataSource).load().migrate();

            try (var result = statement.executeQuery("SELECT * FROM school_courses")) {
                assertTrue(result.next());
                assertEquals(originalId, result.getLong("id"));
                assertEquals("Math", result.getString("name"));
                assertEquals(1, result.getLong("classroom_id"));
                assertEquals(1, result.getLong("teacher_id"));
                assertFalse(result.next());
            }
            assertThrows(SQLException.class, () -> statement.executeQuery("SELECT * FROM subjects"));
            assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO school_courses(name, classroom_id, teacher_id) VALUES ('Math', 1, 1)"));
            assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO school_courses(name, classroom_id, teacher_id) VALUES ('History', 1, 999)"));
            statement.execute("INSERT INTO school_courses(name, classroom_id, teacher_id) VALUES ('History', 1, 1)");
            try (var result = statement.executeQuery("SELECT id FROM school_courses WHERE name = 'History'")) {
                assertTrue(result.next());
                assertNotEquals(originalId, result.getLong(1));
            }
        }
    }
}
