package pulsoescolar_api.service.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pulsoescolar_api.dto.metrics.TechnicalMetricsResponse;
import pulsoescolar_api.dto.metrics.TechnicalMetricsResponse.Sample;

@Service
@EnableScheduling
public class TechnicalMetricsService {
    private final MeterRegistry registry;
    private final Deque<Sample> history = new ArrayDeque<>();
    private Map<String, Long> previousCounts = Map.of();
    private double previousTimeMs;
    private long previousNanos = System.nanoTime();

    public TechnicalMetricsService(MeterRegistry registry) { this.registry = registry; }

    @Scheduled(initialDelay = 15000, fixedDelay = 15000)
    public synchronized void sample() {
        long now = System.nanoTime();
        double interval = (now - previousNanos) / 1_000_000_000.0;
        var counts = new TreeMap<String, Long>();
        double totalTimeMs = 0;
        for (var timer : registry.find("http.server.requests").timers()) {
            String status = timer.getId().getTag("status");
            counts.merge(status == null ? "UNKNOWN" : status, timer.count(), Long::sum);
            totalTimeMs += timer.totalTime(TimeUnit.MILLISECONDS);
        }
        var rates = new TreeMap<String, Double>();
        long requests = 0, errors = 0;
        for (var entry : counts.entrySet()) {
            long delta = Math.max(0, entry.getValue() - previousCounts.getOrDefault(entry.getKey(), 0L));
            rates.put(entry.getKey(), delta / interval);
            requests += delta;
            if (entry.getKey().startsWith("5")) errors += delta;
        }
        var cpu = gauge("process.cpu.usage");
        history.addLast(new Sample(Instant.now(), valueOrZero(gauge("process.uptime")), interval,
                requests / interval, requests == 0 ? 0 : errors * 100.0 / requests,
                requests == 0 ? 0 : Math.max(0, totalTimeMs - previousTimeMs) / requests,
                Map.copyOf(rates), cpu == null ? null : cpu * 100,
                sumGauges("jvm.memory.used", "area", "heap"),
                sumGauges("jvm.memory.committed", "area", "heap"), gauge("jvm.threads.live"),
                sumGauges("hikaricp.connections.active"), sumGauges("hikaricp.connections.idle")));
        // shortcut: one hour of instance-local samples, use persistent storage for longer or shared history.
        if (history.size() > 240) history.removeFirst();
        previousCounts = counts;
        previousTimeMs = totalTimeMs;
        previousNanos = now;
    }

    public synchronized TechnicalMetricsResponse get() {
        return new TechnicalMetricsResponse(history.peekLast(), List.copyOf(history));
    }

    private Double gauge(String name) {
        var gauge = registry.find(name).gauge();
        if (gauge == null) return null;
        double value = gauge.value();
        return Double.isFinite(value) ? value : null;
    }

    private Double sumGauges(String name, String... tags) {
        var gauges = registry.find(name).tags(tags).gauges();
        double sum = 0;
        boolean available = false;
        for (var gauge : gauges) {
            double value = gauge.value();
            if (Double.isFinite(value)) { sum += value; available = true; }
        }
        return available ? sum : null;
    }

    private double valueOrZero(Double value) { return value == null ? 0 : value; }
}
