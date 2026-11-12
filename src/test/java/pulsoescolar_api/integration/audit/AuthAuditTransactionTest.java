package pulsoescolar_api.integration.audit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pulsoescolar_api.service.audit.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AuthAuditTransactionTest {
    @Autowired AuthAuditService audit;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @Test void successRollsBackButFailureIsPreserved() {
        var marker = -System.nanoTime();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            audit.record(AuthAuditEvent.LOGIN_SUCCEEDED, marker);
            audit.recordFailure(AuthAuditEvent.LOGIN_FAILED, marker);
            status.setRollbackOnly();
        });
        var events = jdbc.queryForList("SELECT event_type FROM authentication_events WHERE actor_user_id=?", String.class, marker);
        assertThat(events).containsExactly("LOGIN_FAILED");
    }
}
