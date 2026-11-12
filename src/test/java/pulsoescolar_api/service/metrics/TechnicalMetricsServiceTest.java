package pulsoescolar_api.service.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TechnicalMetricsServiceTest {
    @Test void samplesDeltasRatherThanLifetimeAveragesAndBoundsHistory() {
        var registry = new SimpleMeterRegistry();
        try {
            var service = new TechnicalMetricsService(registry);
            assertThat(service.get().current()).isNull();
            registry.timer("http.server.requests", "status", "200").record(10, TimeUnit.MILLISECONDS);
            registry.timer("http.server.requests", "status", "500").record(30, TimeUnit.MILLISECONDS);
            service.sample();
            var first = service.get().current();
            assertThat(first.http5xxPercent()).isEqualTo(50);
            assertThat(first.averageResponseMs()).isCloseTo(20, within(0.001));
            assertThat(first.requestsPerSecond() * first.intervalSeconds()).isCloseTo(2, within(0.001));
            assertThat(first.processCpuPercent()).isNull();
            registry.timer("http.server.requests", "status", "200").record(5, TimeUnit.MILLISECONDS);
            service.sample();
            assertThat(service.get().current().averageResponseMs()).isCloseTo(5, within(0.001));
            assertThat(service.get().current().http5xxPercent()).isZero();
            for (int i = 0; i < 241; i++) service.sample();
            assertThat(service.get().history()).hasSize(240);
            assertThat(service.get().current().requestsPerSecond()).isZero();
            assertThat(service.get().current().averageResponseMs()).isZero();
        } finally { registry.close(); }
    }
}
