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

    @GetMapping("/{subjectId}/themes")
    public List<String> themes(@PathVariable Long classroomId, @PathVariable Long subjectId) {
        return service.themes(classroomId, subjectId);
    }

    @PostMapping("/{subjectId}/themes")
    @ResponseStatus(CREATED)
    public ThemeResponse addTheme(@PathVariable Long classroomId, @PathVariable Long subjectId,
                                   @Valid @RequestBody ThemeRequest request) {
        return new ThemeResponse(service.addTheme(classroomId, subjectId, request));
    }

    @DeleteMapping("/{subjectId}/themes")
    @ResponseStatus(NO_CONTENT)
    public void removeTheme(@PathVariable Long classroomId, @PathVariable Long subjectId,
                            @Valid @ModelAttribute ThemeRequest request) {
        service.removeTheme(classroomId, subjectId, request.name());
    }

    public record ThemeResponse(String name) {}
}
