package pulsoescolar_api.migration;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

class ProfileAcceptanceMigrationTest {
    @Test void upgradesApplied56WithoutRepairAndPreservesAcceptances() throws Exception {
        var url = "jdbc:h2:mem:profile_" + UUID.randomUUID() + ";MODE=PostgreSQL";
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
            var source = new SingleConnectionDataSource(connection, true);
            Flyway.configure().dataSource(source).target("56").load().migrate();
            try (var rows = sql.executeQuery("SELECT \"checksum\" FROM \"flyway_schema_history\" WHERE \"version\"='56'")) {
                assertTrue(rows.next()); assertEquals(-3206900, rows.getInt(1));
            }
            sql.execute("""
                    INSERT INTO school_users(full_name,ra,email,password_hash,role,terms_accepted)
                    VALUES('Aluno Teste','migration-test','migration@test.invalid','hash','STUDENT',false)
                    """);
            long userId;
            try (var rows = sql.executeQuery("SELECT id FROM school_users WHERE ra='migration-test'")) {
                assertTrue(rows.next()); userId = rows.getLong(1);
            }
            sql.execute("INSERT INTO user_student_term_acceptances(user_id,version) VALUES(" + userId + ",1)");
            var flyway = Flyway.configure().dataSource(source).load();
            flyway.migrate();
            flyway.validate();
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM user_student_term_acceptances")) {
                assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
            }
            sql.execute("DELETE FROM school_users WHERE id=" + userId);
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM user_student_term_acceptances")) {
                assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
            }
        }
    }
}
