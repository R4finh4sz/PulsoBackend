package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V20__one_active_coordinator_per_school extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        try (var statement = connection.createStatement()) {
            // Stop with an actionable error instead of choosing or deleting existing coordinators.
            try (var duplicates = statement.executeQuery("""
                    SELECT school_id FROM school_users
                    WHERE role = 'PEDAGOGICAL_COORDINATOR' AND deleted_at IS NULL AND school_id IS NOT NULL
                    GROUP BY school_id HAVING COUNT(*) > 1
                    """)) {
                if (duplicates.next()) {
                    throw new IllegalStateException("Há escolas com mais de um coordenador ativo. "
                            + "Regularize os vínculos antes de executar a migração V20. Escola: " + duplicates.getLong(1));
                }
            }
            if ("PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
                statement.execute("""
                        CREATE UNIQUE INDEX uk_school_active_coordinator ON school_users (school_id)
                        WHERE role = 'PEDAGOGICAL_COORDINATOR' AND deleted_at IS NULL
                        """);
            } else if ("H2".equals(connection.getMetaData().getDatabaseProductName())) {
                statement.execute("""
                        ALTER TABLE school_users ADD COLUMN active_coordinator_school_id BIGINT
                        GENERATED ALWAYS AS (CASE WHEN role = 'PEDAGOGICAL_COORDINATOR' AND deleted_at IS NULL
                        THEN school_id ELSE NULL END)
                        """);
                statement.execute("CREATE UNIQUE INDEX uk_school_active_coordinator ON school_users (active_coordinator_school_id)");
            } else {
                throw new IllegalStateException("Banco não suportado para a restrição de coordenador único.");
            }
        }
    }
}
