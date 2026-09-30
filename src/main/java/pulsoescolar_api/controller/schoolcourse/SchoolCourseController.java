package pulsoescolar_api.controller.schoolcourse;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pulsoescolar_api.dto.schoolcourse.NameRequest;
import pulsoescolar_api.dto.schoolcourse.SchoolCourseResponse;
import pulsoescolar_api.service.schoolcourse.SchoolCourseService;
import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/api/classrooms/{id}/school-courses")
@RequiredArgsConstructor
public class SchoolCourseController {
    private final SchoolCourseService service;

    @PostMapping
    @ResponseStatus(CREATED)
    public SchoolCourseResponse schoolCourse(@PathVariable Long id, @Valid @RequestBody NameRequest request) {
        return service.createSchoolCourse(id, request);
    }

    @GetMapping
    public List<SchoolCourseResponse> schoolCourses(@PathVariable Long id) {
        return service.schoolCourses(id);
    }
}
