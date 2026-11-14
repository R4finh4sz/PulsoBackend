package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V50__audit_log_retention extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var database = context.getConnection().getMetaData().getDatabaseProductName();
        if (database.equals("H2")) return;
        if (!database.equals("PostgreSQL")) {
            throw new IllegalStateException("Auditoria suporta PostgreSQL e H2 de testes.");
        }
        try (var sql = context.getConnection().createStatement()) {
            sql.execute("""
                    CREATE FUNCTION allow_expired_audit_deletion() RETURNS trigger
                    LANGUAGE plpgsql AS $$
                    BEGIN
                        IF OLD.occurred_at < (((CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - INTERVAL '1 year') AT TIME ZONE 'UTC') THEN
                            RETURN OLD;
                        END IF;
                        RAISE EXCEPTION 'Audit logs must be retained for one year' USING ERRCODE = '42501';
                    END;
                    $$
                    """);
            for (var table : new String[]{"audit_events", "authentication_events"}) {
                sql.execute("DROP TRIGGER prevent_audit_mutation ON " + table);
                sql.execute("CREATE TRIGGER prevent_audit_mutation BEFORE UPDATE OR TRUNCATE ON "
                        + table + " FOR EACH STATEMENT EXECUTE FUNCTION reject_audit_mutation()");
                sql.execute("ALTER TABLE " + table + " ENABLE ALWAYS TRIGGER prevent_audit_mutation");
                sql.execute("CREATE TRIGGER enforce_audit_retention BEFORE DELETE ON "
                        + table + " FOR EACH ROW EXECUTE FUNCTION allow_expired_audit_deletion()");
                sql.execute("ALTER TABLE " + table + " ENABLE ALWAYS TRIGGER enforce_audit_retention");
            }
        }
    }
}
