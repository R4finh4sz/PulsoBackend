package pulsoescolar_api.service.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import pulsoescolar_api.exception.auth.LoginLockedException;

@Service
public class LoginAttemptService {
    static final int MAX_FAILURES = 5;
    static final Duration LOCK = Duration.ofMinutes(15);
    private static final int MAX_ENTRIES = 50_000;

    private final Clock clock;
    private final Map<String, Entry> entries = new HashMap<>();

    public LoginAttemptService(Clock clock) { this.clock = clock; }

    private static final class Entry {
        int failures;
        Instant lockedUntil;
    }

    public synchronized void requireNotLocked(String email) {
        var entry = entries.get(email);
        if (entry == null || entry.lockedUntil == null) return;
        var now = clock.instant();
        if (now.isBefore(entry.lockedUntil)) {
            throw new LoginLockedException(Math.max(1,
                    (Duration.between(now, entry.lockedUntil).toMillis() + 999) / 1000));
        }
        entries.remove(email);
    }

    public synchronized void recordFailure(String email) {
        var now = clock.instant();
        if (entries.size() >= MAX_ENTRIES && !entries.containsKey(email)) {
            entries.values().removeIf(e -> e.lockedUntil != null && !now.isBefore(e.lockedUntil));
            if (entries.size() >= MAX_ENTRIES) return;
        }
        var entry = entries.computeIfAbsent(email, key -> new Entry());
        if (entry.lockedUntil != null && !now.isBefore(entry.lockedUntil)) {
            entry.failures = 0;
            entry.lockedUntil = null;
        }
        if (++entry.failures >= MAX_FAILURES) entry.lockedUntil = now.plus(LOCK);
    }

    public synchronized void recordSuccess(String email) { entries.remove(email); }
}
