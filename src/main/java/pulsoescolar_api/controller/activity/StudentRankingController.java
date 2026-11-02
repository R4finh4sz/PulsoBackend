package pulsoescolar_api.controller.activity;

import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pulsoescolar_api.dto.activity.StudentRankingResponse;
import pulsoescolar_api.service.activity.StudentRankingService;

@RestController @RequestMapping("/api/rankings/students") @RequiredArgsConstructor
public class StudentRankingController {
    private final StudentRankingService service;

    @GetMapping
    public List<StudentRankingResponse> ranking(@RequestParam int year, @RequestParam int bimester,
            @RequestParam(required = false) @Positive Long schoolId) {
        return service.ranking(year, bimester, schoolId);
    }
}
