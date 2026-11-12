package pulsoescolar_api.migration;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

class QuizGradingMigrationTests {
    @Test
    void allowsAutomaticGradingPreservingHistoryAndRejectingPartialResults() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:quiz_grading_migration;MODE=PostgreSQL", "sa", "");
             var statement = connection.createStatement()) {
            var source = new SingleConnectionDataSource(connection, true);
            Flyway.configure().dataSource(source).target("28").load().migrate();
            statement.execute("INSERT INTO classrooms(id, name, identifier) VALUES (1, 'Class', 'A')");
            statement.execute("""
                    INSERT INTO school_users(id, full_name, ra, email, password_hash, role)
                    VALUES (1, 'Teacher', 'T1', 'teacher@test', 'unused', 'TEACHER'),
                           (2, 'Student', 'S1', 'student@test', 'unused', 'STUDENT')
                    """);
            statement.execute("INSERT INTO classroom_teachers VALUES (1, 1)");
            statement.execute("INSERT INTO subjects(id, name, classroom_id, teacher_id) VALUES (1, 'Math', 1, 1)");
            statement.execute("INSERT INTO quiz_activities(id, subject_id, created_by_id, created_at) VALUES (1, 1, 1, CURRENT_TIMESTAMP)");
            statement.execute("""
                    INSERT INTO quiz_submissions(id, activity_id, student_id, submitted_at)
                    VALUES (1, 1, 2, CURRENT_TIMESTAMP)
                    """);
            String grade = "UPDATE quiz_submissions SET graded_at = CURRENT_TIMESTAMP, correct_answers = 3, score = 30 WHERE id = 1";
            assertThrows(SQLException.class, () -> statement.execute(grade));
            Flyway.configure().dataSource(source).load().migrate();
            statement.execute(grade);
            try (var result = statement.executeQuery("SELECT score, graded_by_id FROM quiz_submissions WHERE id = 1")) {
                assertTrue(result.next());
                assertEquals(30, result.getInt("score"));
                assertNull(result.getObject("graded_by_id"));
            }
            assertThrows(SQLException.class, () -> statement.execute("UPDATE quiz_submissions SET score = NULL WHERE id = 1"));
            assertThrows(SQLException.class, () -> statement.execute("UPDATE quiz_submissions SET score = 101 WHERE id = 1"));
            statement.execute("UPDATE quiz_submissions SET graded_by_id = 1 WHERE id = 1");
        }
    }
}
