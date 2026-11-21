package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Turmas apagadas/formadas (deleted_at preenchido) não bloqueiam mais a recriação do mesmo nome/identificador. */
public class V59__classroom_unique_among_active extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        var product = connection.getMetaData().getDatabaseProductName();
        try (var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE classrooms DROP CONSTRAINT uk_classroom_school_name_identifier");
            if ("PostgreSQL".equals(product)) {
                statement.execute("""
                        CREATE UNIQUE INDEX uk_classroom_school_name_identifier_active
                        ON classrooms (school_id, name, identifier) WHERE deleted_at IS NULL
                        """);
            } else if ("H2".equals(product)) {
                statement.execute("""
                        ALTER TABLE classrooms ADD COLUMN active_flag SMALLINT
                        GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN 1 ELSE NULL END)
                        """);
                statement.execute("""
                        CREATE UNIQUE INDEX uk_classroom_school_name_identifier_active
                        ON classrooms (school_id, name, identifier, active_flag)
                        """);
            } else {
                throw new IllegalStateException("Banco não suportado para a restrição de turma única.");
            }
        }
    }
}
