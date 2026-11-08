package pulsoescolar_api.controller.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import pulsoescolar_api.dto.metrics.*;
import pulsoescolar_api.service.metrics.*;

@RestController
@RequiredArgsConstructor
public class MetricsController {
    private final TechnicalMetricsService technical;
    private final SchoolMetricsService school;

    @GetMapping("/api/admin/metrics")
    public ResponseEntity<TechnicalMetricsResponse> technical() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(technical.get());
    }

    @GetMapping("/api/schools/me/metrics")
    public ResponseEntity<SchoolMetricsResponse> school() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(school.get());
    }

    @GetMapping("/api/schools/me/metrics/growth")
    public ResponseEntity<SchoolGrowthResponse> growth(@RequestParam int year) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(school.growth(year));
    }
}
