package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V57__profile_document_acceptance extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        var database = connection.getMetaData().getDatabaseProductName();
        if (!database.equals("PostgreSQL") && !database.equals("H2")) {
            throw new IllegalStateException("Documentos suportam PostgreSQL e H2 de testes.");
        }
        var table = database.equals("H2") ? "USER_STUDENT_TERM_ACCEPTANCES" : "user_student_term_acceptances";
        String foreignKey = null;
        try (var keys = connection.getMetaData().getImportedKeys(connection.getCatalog(), connection.getSchema(), table)) {
            while (keys.next()) {
                if ("user_id".equalsIgnoreCase(keys.getString("FKCOLUMN_NAME"))) {
                    foreignKey = keys.getString("FK_NAME");
                    break;
                }
            }
        }
        if (foreignKey == null) throw new IllegalStateException("Vinculo do aceite estudantil nao encontrado.");
        try (var sql = connection.createStatement()) {
            sql.execute("ALTER TABLE registration_requests ALTER COLUMN terms_version DROP NOT NULL");
            sql.execute("ALTER TABLE guardian_invitations ADD COLUMN privacy_version INTEGER");
            // Constraint names differ between PostgreSQL and H2; preserve existing acceptance rows.
            sql.execute("ALTER TABLE user_student_term_acceptances DROP CONSTRAINT \"" + foreignKey.replace("\"", "\"\"") + "\"");
            sql.execute("""
                    ALTER TABLE user_student_term_acceptances ADD CONSTRAINT fk_student_term_user
                    FOREIGN KEY (user_id) REFERENCES school_users(id) ON DELETE CASCADE
                    """);
        }
    }
}
