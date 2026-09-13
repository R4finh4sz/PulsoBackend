package pulsoescolar_api.controller.classroom;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import pulsoescolar_api.dto.classroom.UpdateClassroomRequest;
import pulsoescolar_api.dto.user.UserResponse;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pulsoescolar_api.dto.classroom.ClassroomResponse;
import pulsoescolar_api.dto.classroom.CreateClassroomRequest;
import pulsoescolar_api.service.classroom.ClassroomService;
import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/api/classrooms")
@RequiredArgsConstructor
public class ClassroomController {
    private final ClassroomService service;

    @GetMapping("/{id}")
    public ClassroomResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    public ClassroomResponse update(@PathVariable Long id,
            @Valid @RequestBody UpdateClassroomRequest request) {
        return service.update(id, request);
    }

    @GetMapping("/{id}/teachers")
    public List<UserResponse> teachers(
            @PathVariable Long id) {
        return service.teachers(id);
    }

    @PostMapping
    @ResponseStatus(CREATED)
    public ClassroomResponse create(@Valid @RequestBody CreateClassroomRequest request) {
        return service.createClassroom(request);
    }

    @GetMapping
    public List<ClassroomResponse> classrooms() {
        return service.listClassrooms();
    }
}
