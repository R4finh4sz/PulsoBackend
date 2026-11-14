package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V49__readable_audit_events extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var database = context.getConnection().getMetaData().getDatabaseProductName();
        if (!database.equals("PostgreSQL") && !database.equals("H2")) {
            throw new IllegalStateException("Auditoria suporta PostgreSQL e H2 de testes.");
        }
        try (var sql = context.getConnection().createStatement()) {
            // Generated names keep historical rows readable without updating append-only logs.
            sql.execute("""
                    ALTER TABLE audit_events ADD COLUMN event_name VARCHAR(40) GENERATED ALWAYS AS (
                        CASE event_type
                            WHEN 1 THEN 'TERMS_ACCEPTED'
                            WHEN 2 THEN 'ACCOUNT_DELETED'
                            WHEN 3 THEN 'SCHOOL_CREATED'
                            WHEN 4 THEN 'COORDINATOR_CREATED'
                        END
                    )
                    """ + (database.equals("PostgreSQL") ? " STORED" : ""));
            sql.execute("""
                    CREATE VIEW audit_event_details AS
                    SELECT occurred_at, event_type, event_name, actor_user_id FROM audit_events
                    """);
            sql.execute("DROP VIEW audit_events_grafana");
        }
    }
}
