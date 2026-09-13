package pulsoescolar_api.controller.teacher;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pulsoescolar_api.dto.classroom.ClassroomResponse;
import pulsoescolar_api.service.teacher.TeacherAssignmentService;

@RestController
@RequestMapping("/api/classrooms/{id}/teachers")
@RequiredArgsConstructor
public class TeacherAssignmentController {
    private final TeacherAssignmentService service;

    @PutMapping("/{teacherId}")
    public ClassroomResponse assign(@PathVariable Long id, @PathVariable Long teacherId) {
        return service.assign(id, teacherId);
    }
}
