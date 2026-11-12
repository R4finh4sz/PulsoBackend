package pulsoescolar_api.dto.metrics;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TechnicalMetricsResponse(Sample current, List<Sample> history) {
    public record Sample(Instant timestamp, double uptimeSeconds, double intervalSeconds,
            double requestsPerSecond, double http5xxPercent, double averageResponseMs,
            Map<String, Double> requestsPerSecondByStatus, Double processCpuPercent,
            Double heapUsedBytes, Double heapCommittedBytes, Double liveThreads,
            Double databaseActiveConnections, Double databaseIdleConnections) {}
}
