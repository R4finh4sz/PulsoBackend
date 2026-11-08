package pulsoescolar_api.migration;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

class AuthenticationAuditMigrationTest {
    @Test void addsAuditWithoutChangingExistingEventsAndRejectsUnknownCategories() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:authentication_audit_migration;MODE=PostgreSQL", "sa", "");
             var sql = connection.createStatement()) {
            var source = new SingleConnectionDataSource(connection, true);
            Flyway.configure().dataSource(source).target("38").load().migrate();
            sql.execute("INSERT INTO audit_events(occurred_at,event_type) VALUES(CURRENT_TIMESTAMP,1)");
            Flyway.configure().dataSource(source).target("39").load().migrate();
            sql.execute("INSERT INTO authentication_events(occurred_at,event_type) VALUES(CURRENT_TIMESTAMP,'LOGIN_FAILED')");
            sql.execute("ALTER TABLE authentication_events ADD CONSTRAINT ck_auth_time CHECK (occurred_at IS NOT NULL)");
            Flyway.configure().dataSource(source).target("40").load().migrate();
            try (var rows = sql.executeQuery("SELECT event_type,actor_user_id FROM authentication_events")) {
                assertTrue(rows.next()); assertEquals("LOGIN_FAILED", rows.getString(1)); assertNull(rows.getObject(2));
            }
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM audit_events")) {
                assertTrue(rows.next()); assertEquals(1, rows.getLong(1));
            }
            assertThrows(SQLException.class, () -> sql.execute("INSERT INTO authentication_events(occurred_at,event_type) VALUES(CURRENT_TIMESTAMP,'PASSWORD=secret')"));
            sql.execute("INSERT INTO authentication_events(occurred_at,event_type) VALUES(CURRENT_TIMESTAMP,'PASSWORD_RECOVERY_REQUESTED')");
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_name='CK_AUTH_TIME'")) {
                assertTrue(rows.next()); assertEquals(1, rows.getLong(1));
            }
        }
    }
}
