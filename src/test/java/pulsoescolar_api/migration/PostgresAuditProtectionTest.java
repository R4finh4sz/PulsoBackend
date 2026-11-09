package pulsoescolar_api.migration;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PostgresAuditProtectionTest {
    @Test void postgresRejectsUpdateDeleteAndTruncateForBothAuditTables() throws Exception {
        var url = System.getenv("PULSO_TEST_POSTGRES_URL");
        assumeTrue(url != null && (url.startsWith("jdbc:postgresql://localhost:") || url.startsWith("jdbc:postgresql://127.0.0.1:")),
                "Requer PostgreSQL local e isolado, com porta explícita.");
        var schema = "audit_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, System.getenv("PULSO_TEST_POSTGRES_USER"),
                System.getenv("PULSO_TEST_POSTGRES_PASSWORD")); var sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
            try {
                connection.setSchema(schema);
                Flyway.configure().dataSource(new SingleConnectionDataSource(connection, true))
                        .schemas(schema).defaultSchema(schema).target("39").load().migrate();
                connection.setSchema(schema);
                sql.execute("INSERT INTO audit_events(occurred_at,event_type) VALUES(CURRENT_TIMESTAMP,1)");
                sql.execute("INSERT INTO authentication_events(occurred_at,event_type) VALUES(CURRENT_TIMESTAMP,'LOGIN_FAILED')");
                sql.execute("ALTER TABLE authentication_events ADD CONSTRAINT ck_auth_time CHECK (occurred_at IS NOT NULL)");
                Flyway.configure().dataSource(new SingleConnectionDataSource(connection, true))
                        .schemas(schema).defaultSchema(schema).target("40").load().migrate();
                connection.setSchema(schema);
                for (String table : new String[]{"audit_events", "authentication_events"}) {
                    for (String mutation : new String[]{"UPDATE " + table + " SET actor_user_id=1", "DELETE FROM " + table, "TRUNCATE " + table}) {
                        assertEquals("42501", assertThrows(SQLException.class, () -> sql.execute(mutation)).getSQLState());
                    }
                    try (var rows = sql.executeQuery("SELECT COUNT(*) FROM " + table)) {
                        assertTrue(rows.next()); assertEquals(1, rows.getLong(1));
                    }
                }
                sql.execute("INSERT INTO authentication_events(occurred_at,event_type) VALUES(CURRENT_TIMESTAMP,'PASSWORD_RECOVERY_REQUESTED')");
                assertThrows(SQLException.class, () -> sql.execute("INSERT INTO authentication_events(occurred_at,event_type) VALUES(CURRENT_TIMESTAMP,'UNKNOWN')"));
            } finally {
                connection.setSchema("public");
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
