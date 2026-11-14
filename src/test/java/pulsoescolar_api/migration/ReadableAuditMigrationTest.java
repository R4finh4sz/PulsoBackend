package pulsoescolar_api.migration;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

class ReadableAuditMigrationTest {
    @Test void existingAndNewEventsHaveNamesWithoutChangingCodesOrActors() throws Exception {
        var url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
            var source = new SingleConnectionDataSource(connection, true);
            Flyway.configure().dataSource(source).target("48").load().migrate();
            sql.execute("INSERT INTO audit_events(occurred_at,event_type,actor_user_id) VALUES(CURRENT_TIMESTAMP,1,42)");
            Flyway.configure().dataSource(source).load().migrate();
            for (int code = 2; code <= 4; code++) {
                sql.execute("INSERT INTO audit_events(occurred_at,event_type) VALUES(CURRENT_TIMESTAMP," + code + ")");
            }
            try (var rows = sql.executeQuery("SELECT event_type,event_name,actor_user_id FROM audit_event_details ORDER BY event_type")) {
                var names = new String[]{"TERMS_ACCEPTED", "ACCOUNT_DELETED", "SCHOOL_CREATED", "COORDINATOR_CREATED"};
                for (int code = 1; code <= 4; code++) {
                    assertTrue(rows.next());
                    assertEquals(code, rows.getInt("event_type"));
                    assertEquals(names[code - 1], rows.getString("event_name"));
                    assertEquals(code == 1 ? 42L : null, rows.getObject("actor_user_id", Long.class));
                }
                assertFalse(rows.next());
            }
            try (var rows = sql.executeQuery("SELECT event_name FROM audit_events WHERE event_type=1")) {
                assertTrue(rows.next()); assertEquals("TERMS_ACCEPTED", rows.getString(1));
            }
            assertThrows(java.sql.SQLException.class, () -> sql.executeQuery("SELECT * FROM audit_events_grafana"));
        }
    }
}
