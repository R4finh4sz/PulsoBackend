package pulsoescolar_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pulsoescolar_api.dto.UserResponse;
import pulsoescolar_api.service.StudentEnrollmentService;

@RestController
@RequestMapping("/api/classrooms/{id}/students")
@RequiredArgsConstructor
public class StudentEnrollmentController {
    private final StudentEnrollmentService service;

    @PutMapping("/{studentId}")
    public UserResponse enroll(@PathVariable Long id, @PathVariable Long studentId) {
        return service.enroll(id, studentId);
    }
}
