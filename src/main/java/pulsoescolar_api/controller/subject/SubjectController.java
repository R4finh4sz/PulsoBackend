package pulsoescolar_api.controller.subject;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pulsoescolar_api.dto.subject.*;
import pulsoescolar_api.service.subject.SubjectService;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/classrooms/{classroomId}/subjects")
@RequiredArgsConstructor
public class SubjectController {
    private final SubjectService service;

    @GetMapping
    public List<SubjectResponse> list(@PathVariable Long classroomId) {
        return service.subjects(classroomId);
    }

    @PostMapping
    @ResponseStatus(CREATED)
    public SubjectResponse create(@PathVariable Long classroomId, @Valid @RequestBody CreateSubjectRequest request) {
        return service.createSubject(classroomId, request);
    }

}
