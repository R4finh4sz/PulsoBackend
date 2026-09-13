package pulsoescolar_api.controller.teacher;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

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

    @DeleteMapping("/{teacherId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassign(@PathVariable Long id, @PathVariable Long teacherId) {
        service.unassign(id, teacherId);
    }

    @PutMapping("/{teacherId}")
    public ClassroomResponse assign(@PathVariable Long id, @PathVariable Long teacherId) {
        return service.assign(id, teacherId);
    }
}
