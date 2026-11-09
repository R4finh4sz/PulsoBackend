package pulsoescolar_api.migration;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

class TermsRevocationMigrationTest {
    @Test void storesRevocationDatesWithUserReferences() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:terms_revocation_migration;MODE=PostgreSQL", "sa", "");
             var sql = connection.createStatement()) {
            var source = new SingleConnectionDataSource(connection, true);
            Flyway.configure().dataSource(source).target("35").load().migrate();
            sql.execute("INSERT INTO school_users(id,full_name,ra,email,password_hash,role) VALUES(1,'Ana','RA1','ana@test','unused','STUDENT')");
            Flyway.configure().dataSource(source).target("36").load().migrate();
            sql.execute("INSERT INTO terms_revocations(user_id,revoked_at) VALUES(1,CURRENT_TIMESTAMP)");
            try (var rows = sql.executeQuery("SELECT user_id,revoked_at FROM terms_revocations")) {
                assertTrue(rows.next()); assertEquals(1L, rows.getLong(1));
                assertNotNull(rows.getTimestamp(2)); assertFalse(rows.next());
            }
            assertThrows(SQLException.class, () -> sql.execute("INSERT INTO terms_revocations(user_id,revoked_at) VALUES(999,CURRENT_TIMESTAMP)"));
            assertThrows(SQLException.class, () -> sql.execute("INSERT INTO terms_revocations(user_id) VALUES(1)"));
        }
    }
}
