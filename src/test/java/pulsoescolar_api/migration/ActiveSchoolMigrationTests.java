package pulsoescolar_api.migration;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActiveSchoolMigrationTests {
    @Test
    void keepsOnlyEnrollmentSchoolForStudentsAndPreservesTeacherMemberships() throws Exception {
        String url = "jdbc:h2:mem:single_student_school;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
            var dataSource = new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true);
            Flyway.configure().dataSource(dataSource).target("32").load().migrate();
            sql.execute("INSERT INTO schools(id,nome,cnpj,logradouro,bairro,cidade) VALUES (1,'Origem','123','Rua','Centro','Cidade'), (2,'Destino','456','Rua','Centro','Cidade')");
            sql.execute("INSERT INTO school_users(id,full_name,ra,email,password_hash,role,school_id,active_school_id) VALUES (1,'Aluno','RA1','student@test','hash','STUDENT',1,2), (2,'Professor','RA2','teacher@test','hash','TEACHER',1,2)");
            sql.execute("INSERT INTO user_schools(user_id,school_id) VALUES (1,2),(2,1),(2,2)");
            Flyway.configure().dataSource(dataSource).load().migrate();
            try (var rows = sql.executeQuery("SELECT active_school_id FROM school_users WHERE id=1")) {
                assertTrue(rows.next()); assertEquals(1L, rows.getLong(1));
            }
            try (var rows = sql.executeQuery("SELECT school_id FROM user_schools WHERE user_id=1")) {
                assertTrue(rows.next()); assertEquals(1L, rows.getLong(1)); assertFalse(rows.next());
            }
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM user_schools WHERE user_id=2")) {
                assertTrue(rows.next()); assertEquals(2L, rows.getLong(1));
            }
        }
    }

    @Test
    void backfillsExistingSchoolWithoutChangingRegistration() throws Exception {
        String url = "jdbc:h2:mem:active_school_migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
            var dataSource = new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true);
            Flyway.configure().dataSource(dataSource).target("24").load().migrate();
            sql.execute("INSERT INTO schools(id,nome,cnpj,logradouro,bairro,cidade) VALUES (1,'Escola','123','Rua','Centro','Cidade')");
            sql.execute("INSERT INTO school_users(full_name,ra,email,password_hash,role,school_id) VALUES ('Nome','RA','user@test','hash','TEACHER',1)");
            Flyway.configure().dataSource(dataSource).load().migrate();
            try (var rows = sql.executeQuery("SELECT u.school_id, u.active_school_id, s.school_id FROM school_users u JOIN user_schools s ON s.user_id=u.id")) {
                assertTrue(rows.next());
                assertEquals(1L, rows.getLong(1)); assertEquals(1L, rows.getLong(2)); assertEquals(1L, rows.getLong(3));
                assertFalse(rows.next());
            }
        }
    }
}
