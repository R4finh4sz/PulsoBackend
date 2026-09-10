package pulsoescolar_api.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pulsoescolar_api.dto.ClassroomResponse;
import pulsoescolar_api.dto.CreateClassroomRequest;
import pulsoescolar_api.service.ClassroomService;
import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/api/classrooms")
@RequiredArgsConstructor
public class ClassroomController {
    private final ClassroomService service;

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
