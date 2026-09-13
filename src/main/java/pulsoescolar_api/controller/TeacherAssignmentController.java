package pulsoescolar_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pulsoescolar_api.dto.ClassroomResponse;
import pulsoescolar_api.service.TeacherAssignmentService;

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
