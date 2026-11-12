package pulsoescolar_api.controller.activity;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pulsoescolar_api.dto.activity.*;
import pulsoescolar_api.service.activity.ActivityService;
import static org.springframework.http.HttpStatus.CREATED;

@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class ActivityController {
    private final ActivityService service;

    @PostMapping("/subjects/{subjectId}/activities") @ResponseStatus(CREATED)
    public ActivityResponse create(@PathVariable Long subjectId, @Valid @RequestBody CreateActivityRequest input) {
        return service.create(subjectId, input);
    }

    @GetMapping({"/subjects/{subjectId}/activities", "/classrooms/{classroomId}/subjects/{subjectId}/activities"})
    public List<ActivityResponse> list(@PathVariable Long subjectId, @PathVariable(required = false) Long classroomId) {
        return service.list(subjectId, classroomId);
    }

    @GetMapping("/student/activities")
    public List<StudentActivityResponse> studentActivities() { return service.studentActivities(); }

    @GetMapping("/activities/{id}/quiz")
    public QuizResponse open(@PathVariable Long id) { return service.open(id); }

    @PostMapping("/activities/{id}/submissions") @ResponseStatus(CREATED)
    public SubmissionResponse submit(@PathVariable Long id, @Valid @RequestBody SubmitQuizRequest input) {
        return service.submit(id, input);
    }

    @PatchMapping("/submissions/{id}/grade")
    public SubmissionResponse grade(@PathVariable Long id) { return service.grade(id); }

    @GetMapping("/submissions/{id}/result")
    public SubmissionResponse result(@PathVariable Long id) { return service.result(id); }

    @GetMapping("/teacher/activities")
    public List<TeacherActivityResponse> teacherActivities() { return service.teacherActivities(); }
}
