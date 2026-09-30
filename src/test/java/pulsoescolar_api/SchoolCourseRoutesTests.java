package pulsoescolar_api;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pulsoescolar_api.controller.schoolcourse.SchoolCourseController;
import pulsoescolar_api.dto.schoolcourse.NameRequest;
import pulsoescolar_api.dto.schoolcourse.SchoolCourseResponse;
import pulsoescolar_api.service.schoolcourse.SchoolCourseService;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SchoolCourseRoutesTests {
    @Test
    void createsAndListsUsingSchoolCoursesRoute() throws Exception {
        var service = mock(SchoolCourseService.class);
        var course = new SchoolCourseResponse(1L, "Math", 2L, 3L);
        when(service.createSchoolCourse(2L, new NameRequest("Math"))).thenReturn(course);
        when(service.schoolCourses(2L)).thenReturn(List.of(course));
        var mvc = MockMvcBuilders.standaloneSetup(new SchoolCourseController(service)).build();

        mvc.perform(post("/api/classrooms/2/school-courses")
                        .contentType("application/json").content("{\"name\":\"Math\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(1));
        mvc.perform(get("/api/classrooms/2/school-courses"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Math"));
        mvc.perform(get("/api/classrooms/2/subjects")).andExpect(status().isNotFound());
    }
}
