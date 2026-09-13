package pulsoescolar_api.controller.student;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pulsoescolar_api.dto.user.UserResponse;
import pulsoescolar_api.service.student.StudentEnrollmentService;

@RestController
@RequestMapping("/api/classrooms/{id}/students")
@RequiredArgsConstructor
public class StudentEnrollmentController {
    private final StudentEnrollmentService service;

    @DeleteMapping("/{studentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unenroll(@PathVariable Long id, @PathVariable Long studentId) {
        service.unenroll(id, studentId);
    }

    @PutMapping("/{studentId}")
    public UserResponse enroll(@PathVariable Long id, @PathVariable Long studentId) {
        return service.enroll(id, studentId);
    }
}
