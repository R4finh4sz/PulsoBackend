package pulsoescolar_api.controller.user.teacher;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pulsoescolar_api.dto.classroom.ClassroomResponse;
import pulsoescolar_api.service.classroom.ClassroomService;

@RestController
@RequestMapping("/api/teacher/classrooms")
@RequiredArgsConstructor
public class TeacherClassroomController {
    private final ClassroomService service;

    @GetMapping
    public List<ClassroomResponse> classrooms() {
        return service.listClassrooms();
    }
}
