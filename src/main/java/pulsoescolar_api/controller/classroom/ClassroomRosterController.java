package pulsoescolar_api.controller.classroom;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pulsoescolar_api.dto.user.UserResponse;
import pulsoescolar_api.service.classroom.ClassroomRosterService;

@RestController
@RequestMapping("/api/classrooms/{id}/students")
@RequiredArgsConstructor
public class ClassroomRosterController {
    private final ClassroomRosterService service;

    @GetMapping
    public List<UserResponse> students(@PathVariable Long id) {
        return service.students(id);
    }
}
