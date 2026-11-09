package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V40__password_recovery_audit extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        String constraint;
        try (var query = connection.prepareStatement("""
                SELECT t.constraint_name FROM information_schema.table_constraints t
                JOIN information_schema.check_constraints c
                  ON c.constraint_catalog = t.constraint_catalog
                 AND c.constraint_schema = t.constraint_schema AND c.constraint_name = t.constraint_name
                WHERE LOWER(t.table_name) = 'authentication_events' AND t.constraint_type = 'CHECK'
                  AND t.table_schema = ? AND c.check_clause LIKE '%''LOGIN_SUCCEEDED''%'
                """)) {
            query.setString(1, connection.getSchema());
            try (var rows = query.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("Authentication event constraint missing");
                constraint = rows.getString(1);
                if (rows.next()) throw new IllegalStateException("Ambiguous authentication event constraint");
            }
        }
        try (var sql = connection.createStatement()) {
            sql.execute("ALTER TABLE authentication_events DROP CONSTRAINT \"" + constraint.replace("\"", "\"\"") + "\"");
            sql.execute("""
                    ALTER TABLE authentication_events ADD CONSTRAINT ck_authentication_event_type
                    CHECK (event_type IN ('LOGIN_SUCCEEDED','LOGIN_FAILED','LOGIN_2FA_PENDING',
                        'TWO_FACTOR_SUCCEEDED','TWO_FACTOR_FAILED','LOGOUT','TOKEN_REFRESHED',
                        'TOKEN_REFRESH_FAILED','REFRESH_TOKEN_REUSED','PASSWORD_RECOVERY_REQUESTED'))
                    """);
        }
    }
}
