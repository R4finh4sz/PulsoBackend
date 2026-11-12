package pulsoescolar_api.migration;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenMigrationTest {
    @Test void deletingSessionRemovesCurrentAndConsumedRefreshTokens() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:refresh_migration;MODE=PostgreSQL", "sa", "");
             var sql = connection.createStatement()) {
            Flyway.configure().dataSource(new SingleConnectionDataSource(connection, true)).target("38").load().migrate();
            sql.execute("INSERT INTO school_users(id,full_name,ra,email,password_hash,role) VALUES(1,'Ana','RA1','ana@test','unused','STUDENT')");
            sql.execute("""
                    INSERT INTO auth_sessions(id,user_id,created_at,expires_at,two_factor_verified)
                    VALUES('00000000-0000-0000-0000-000000000001',1,CURRENT_TIMESTAMP,DATEADD('HOUR',1,CURRENT_TIMESTAMP),TRUE)
                    """);
            sql.execute("""
                    INSERT INTO refresh_tokens(token_hash,session_id,used_at) VALUES
                    ('old','00000000-0000-0000-0000-000000000001',CURRENT_TIMESTAMP),
                    ('current','00000000-0000-0000-0000-000000000001',NULL)
                    """);
            sql.execute("DELETE FROM auth_sessions WHERE user_id=1");
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM refresh_tokens")) {
                assertTrue(rows.next()); assertEquals(0, rows.getLong(1));
            }
        }
    }
}
