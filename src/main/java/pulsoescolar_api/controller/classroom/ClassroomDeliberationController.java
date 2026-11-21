package pulsoescolar_api.controller.classroom;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pulsoescolar_api.dto.classroom.DeliberateClassroomRequest;
import pulsoescolar_api.dto.classroom.DeliberationResponse;
import pulsoescolar_api.service.classroom.ClassroomDeliberationService;
import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/api/classroom-deliberations")
@RequiredArgsConstructor
public class ClassroomDeliberationController {
    private final ClassroomDeliberationService service;

    @PostMapping
    @ResponseStatus(CREATED)
    public DeliberationResponse deliberate(@Valid @RequestBody DeliberateClassroomRequest request) {
        return service.deliberate(request);
    }

    @GetMapping("/destinations")
    public java.util.List<pulsoescolar_api.dto.classroom.ClassroomResponse> destinations(
            @RequestParam Long classroomId, @RequestParam pulsoescolar_api.entity.classroom.DeliberationResult result) {
        return service.destinations(classroomId, result);
    }
}
