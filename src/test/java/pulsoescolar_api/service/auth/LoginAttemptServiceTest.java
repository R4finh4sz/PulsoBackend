package pulsoescolar_api.service.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import pulsoescolar_api.exception.auth.LoginLockedException;
import static org.assertj.core.api.Assertions.*;

class LoginAttemptServiceTest {
    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptService service = new LoginAttemptService(clock);

    @Test
    void locksForFifteenMinutesAfterFiveFailures() {
        for (int i = 0; i < 4; i++) service.recordFailure("a@x.com");
        assertThatCode(() -> service.requireNotLocked("a@x.com")).doesNotThrowAnyException();
        service.recordFailure("a@x.com");
        assertThatThrownBy(() -> service.requireNotLocked("a@x.com")).isInstanceOf(LoginLockedException.class)
                .satisfies(ex -> assertThat(((LoginLockedException) ex).getRetryAfterSeconds()).isEqualTo(900));
        assertThatCode(() -> service.requireNotLocked("b@x.com")).doesNotThrowAnyException();
        clock.now = clock.now.plus(Duration.ofMinutes(15));
        assertThatCode(() -> service.requireNotLocked("a@x.com")).doesNotThrowAnyException();
    }

    @Test
    void successResetsCounter() {
        for (int i = 0; i < 4; i++) service.recordFailure("a@x.com");
        service.recordSuccess("a@x.com");
        service.recordFailure("a@x.com");
        assertThatCode(() -> service.requireNotLocked("a@x.com")).doesNotThrowAnyException();
    }
}
