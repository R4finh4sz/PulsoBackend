package db.migration;

import java.sql.Connection;
import java.util.ArrayList;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V2__classroom_identifier extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        var constraints = new ArrayList<String>();
        try (var query = connection.prepareStatement("""
                SELECT tc.constraint_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_catalog = kcu.constraint_catalog
                 AND tc.constraint_schema = kcu.constraint_schema
                 AND tc.constraint_name = kcu.constraint_name
                WHERE LOWER(tc.table_name) = 'classrooms'
                  AND tc.table_schema = ?
                  AND tc.constraint_type = 'UNIQUE'
                GROUP BY tc.constraint_name
                HAVING COUNT(*) = 1 AND LOWER(MAX(kcu.column_name)) = 'name'
                """)) {
            query.setString(1, connection.getSchema());
            try (var result = query.executeQuery()) {
                while (result.next()) {
                    constraints.add(result.getString(1));
                }
            }
        }
        try (var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE classrooms ADD COLUMN identifier VARCHAR(1)");
            statement.execute("UPDATE classrooms SET identifier = 'A'");
            statement.execute("ALTER TABLE classrooms ALTER COLUMN identifier SET NOT NULL");
            statement.execute("""
                    ALTER TABLE classrooms ADD CONSTRAINT ck_classroom_identifier
                    CHECK (CHAR_LENGTH(identifier) = 1 AND ASCII(identifier) BETWEEN 65 AND 90)
                    """);
            for (String constraint : constraints) {
                statement.execute("ALTER TABLE classrooms DROP CONSTRAINT \"" + constraint.replace("\"", "\"\"") + "\"");
            }
            statement.execute("""
                    ALTER TABLE classrooms ADD CONSTRAINT uk_classroom_name_identifier
                    UNIQUE (name, identifier)
                    """);
        }
    }
}
