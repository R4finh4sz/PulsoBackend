package pulsoescolar_api.controller.activity;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pulsoescolar_api.dto.activity.ActivityStatus;
import pulsoescolar_api.dto.activity.TeacherReportResponse;
import pulsoescolar_api.service.activity.TeacherReportService;

@RestController
@RequiredArgsConstructor
public class TeacherReportController {
    private final TeacherReportService service;

    @GetMapping("/api/teacher/reports")
    public TeacherReportResponse report(@RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Long classroomId, @RequestParam(required = false) ActivityStatus status) {
        return service.report(schoolId, classroomId, status);
    }
}
