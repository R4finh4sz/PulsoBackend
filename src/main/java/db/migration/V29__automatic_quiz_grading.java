package db.migration;

import java.util.ArrayList;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V29__automatic_quiz_grading extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        var constraints = new ArrayList<String>();
        try (var statement = connection.createStatement()) {
            // V27 left this check unnamed, so its generated name differs across databases.
            try (var result = statement.executeQuery("""
                    SELECT tc.constraint_name FROM information_schema.table_constraints tc
                    JOIN information_schema.check_constraints cc
                      ON cc.constraint_catalog = tc.constraint_catalog
                     AND cc.constraint_schema = tc.constraint_schema
                     AND cc.constraint_name = tc.constraint_name
                    WHERE LOWER(tc.table_name) = 'quiz_submissions'
                      AND tc.table_schema = current_schema
                      AND LOWER(cc.check_clause) LIKE '%graded_by_id%'
                    """)) {
                while (result.next()) constraints.add(result.getString(1));
            }
            if (constraints.size() != 1) {
                throw new IllegalStateException("Expected the V27 quiz grading check constraint.");
            }
            statement.execute("ALTER TABLE quiz_submissions DROP CONSTRAINT \""
                    + constraints.getFirst().replace("\"", "\"\"") + "\"");
            statement.execute("""
                    ALTER TABLE quiz_submissions ADD CONSTRAINT ck_quiz_submission_grading
                    CHECK ((graded_at IS NULL AND graded_by_id IS NULL AND correct_answers IS NULL AND score IS NULL)
                        OR (graded_at IS NOT NULL AND correct_answers IS NOT NULL AND score IS NOT NULL))
                    """);
        }
    }
}
