package pulsoescolar_api.migration;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

class ActivityRecipientsMigrationTest {
    @Test void migratesOnlyProvenRecipientsAndMarksLegacyAsIncomplete() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:activity_recipients_migration;MODE=PostgreSQL", "sa", "");
             var sql = connection.createStatement()) {
            var source = new SingleConnectionDataSource(connection, true);
            Flyway.configure().dataSource(source).target("34").load().migrate();
            sql.execute("INSERT INTO classrooms(id,name,identifier) VALUES(1,'Class','A')");
            sql.execute("""
                    INSERT INTO school_users(id,full_name,ra,email,password_hash,role,classroom_id)
                    VALUES(1,'Teacher','T1','teacher@test','unused','TEACHER',NULL),
                          (2,'Respondent','S1','student@test','unused','STUDENT',1),
                          (3,'Current student','S2','other@test','unused','STUDENT',1)
                    """);
            sql.execute("INSERT INTO classroom_teachers VALUES(1,1)");
            sql.execute("INSERT INTO subjects(id,name,classroom_id,teacher_id) VALUES(1,'Math',1,1)");
            sql.execute("""
                    INSERT INTO quiz_activities(id,subject_id,created_by_id,created_at)
                    VALUES(1,1,1,CURRENT_TIMESTAMP),(2,1,1,CURRENT_TIMESTAMP)
                    """);
            sql.execute("INSERT INTO quiz_submissions(id,activity_id,student_id,submitted_at) VALUES(1,1,2,CURRENT_TIMESTAMP)");
            Flyway.configure().dataSource(source).target("35").load().migrate();
            try (var rows = sql.executeQuery("SELECT activity_id,student_id FROM activity_recipients")) {
                assertTrue(rows.next()); assertEquals(1L, rows.getLong(1)); assertEquals(2L, rows.getLong(2));
                assertFalse(rows.next());
            }
            try (var rows = sql.executeQuery("SELECT recipient_history_complete FROM quiz_activities")) {
                assertTrue(rows.next()); assertFalse(rows.getBoolean(1));
                assertTrue(rows.next()); assertFalse(rows.getBoolean(1)); assertFalse(rows.next());
            }
            assertThrows(SQLException.class, () -> sql.execute("INSERT INTO activity_recipients VALUES(1,2)"));
        }
    }
}
