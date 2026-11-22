package pulsoescolar_api.migration;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrationRequestsMigrationTest {
    @Test void upgradesVersion60WithAnExistingStudentTermAcceptance() throws Exception {
        var url = "jdbc:h2:mem:registration_requests_migration;MODE=PostgreSQL";
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
            var source = new SingleConnectionDataSource(connection, true);
            Flyway.configure().dataSource(source).target("60").load().migrate();

            // Reproduce the deployed schema with a nullable terms_version before V61.
            sql.execute("ALTER TABLE registration_requests ALTER COLUMN terms_version DROP NOT NULL");
            sql.execute("INSERT INTO schools(id,nome,logradouro,bairro,cidade) VALUES (1,'Escola','Rua','Centro','Cidade')");
            sql.execute("""
                    INSERT INTO registration_requests
                        (full_name, ra, email, role, school_id, student_term_version, status, requested_at)
                    VALUES ('Aluno Teste', 'migration-test', 'student@test.invalid', 'STUDENT', 1, 1, 'PENDING', CURRENT_TIMESTAMP)
                    """);

            var flyway = Flyway.configure().dataSource(source).load();
            flyway.migrate();
            assertEquals("62", flyway.info().current().getVersion().getVersion());
            try (var rows = sql.executeQuery("SELECT terms_version, student_term_version FROM registration_requests")) {
                assertTrue(rows.next());
                assertNull(rows.getObject("terms_version"));
                assertEquals(1, rows.getInt("student_term_version"));
            }
            var failure = assertThrows(SQLException.class, () -> sql.executeUpdate(
                    "UPDATE registration_requests SET student_term_version = NULL WHERE ra = 'migration-test'"));
            assertEquals("23513", failure.getSQLState());
        }
    }
}
