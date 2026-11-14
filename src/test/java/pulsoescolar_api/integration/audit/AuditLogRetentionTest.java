package pulsoescolar_api.integration.audit;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.service.audit.AuditLogRetentionJob;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class AuditLogRetentionTest {
    @MockitoBean Clock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuditLogRetentionJob job;

    @Test void purgesOnlyEventsOlderThanOneCalendarYearAndCanRunTwice() {
        var now = Instant.parse("2028-02-29T12:00:00Z");
        when(clock.instant()).thenReturn(now);
        var cutoff = OffsetDateTime.ofInstant(now, ZoneOffset.UTC).minusYears(1);
        long marker = -92341;
        for (var time : new OffsetDateTime[]{cutoff.minusSeconds(1), cutoff, cutoff.plusSeconds(1)}) {
            jdbc.update("INSERT INTO audit_events(occurred_at,event_type,actor_user_id) VALUES(?,1,?)", time, marker);
            jdbc.update("INSERT INTO authentication_events(occurred_at,event_type,actor_user_id) VALUES(?,'LOGIN_FAILED',?)", time, marker);
        }
        for (int run = 0; run < 2; run++) {
            job.purgeExpired();
            for (var table : new String[]{"audit_events", "authentication_events"}) {
                assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE actor_user_id=?", Integer.class, marker));
                assertEquals(cutoff, jdbc.queryForObject("SELECT MIN(occurred_at) FROM " + table + " WHERE actor_user_id=?", OffsetDateTime.class, marker));
            }
        }
    }
}
