package pulsoescolar_api.migration;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

class FaqMigrationTest {
    @Test void createsFaqsAndRestrictsAudience() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:faq_migration;MODE=PostgreSQL", "sa", "");
             var sql = connection.createStatement()) {
            Flyway.configure().dataSource(new SingleConnectionDataSource(connection, true)).target("37").load().migrate();
            sql.execute("INSERT INTO faqs(title,target_role,message) VALUES('Título','TEACHER','Mensagem')");
            try (var rows = sql.executeQuery("SELECT title,target_role,message FROM faqs")) {
                assertTrue(rows.next()); assertEquals("Título", rows.getString(1));
                assertEquals("TEACHER", rows.getString(2)); assertEquals("Mensagem", rows.getString(3));
            }
            assertThrows(SQLException.class, () -> sql.execute("INSERT INTO faqs(title,target_role,message) VALUES('Título','ADMIN','Mensagem')"));
            assertThrows(SQLException.class, () -> sql.execute("INSERT INTO faqs(title,target_role) VALUES('Título','STUDENT')"));
        }
    }
}
