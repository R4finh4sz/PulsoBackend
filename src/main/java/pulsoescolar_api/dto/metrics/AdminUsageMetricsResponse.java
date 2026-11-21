package pulsoescolar_api.dto.metrics;

import java.util.List;
import java.util.Map;
import pulsoescolar_api.entity.user.Role;

public record AdminUsageMetricsResponse(String month, long totalUsers, Map<Role, Long> usersByRole,
                                        List<StateDemand> demandByState) {
    public record StateDemand(String state, long requests) {}
}
