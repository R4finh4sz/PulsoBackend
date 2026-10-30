package pulsoescolar_api.controller.subject;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pulsoescolar_api.dto.subject.CreateSubjectRequest;
import pulsoescolar_api.dto.subject.SubjectResponse;
import pulsoescolar_api.service.subject.SubjectService;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SubjectRoutesTests {
    @Test
    void createsAndListsUsingSubjectsRoute() throws Exception {
        var service = mock(SubjectService.class);
        var subject = new SubjectResponse(1L, "Math", 2L, 3L, "", List.of(), List.of(3L));
        var request = new CreateSubjectRequest("Math", "", List.of(new CreateSubjectRequest.Topic("Theme", "Description", "")));
        when(service.createSubject(2L, request)).thenReturn(subject);
        when(service.subjects(2L)).thenReturn(List.of(subject));
        var mvc = MockMvcBuilders.standaloneSetup(new SubjectController(service)).build();

        mvc.perform(post("/api/classrooms/2/subjects")
                        .contentType("application/json").content("{\"name\":\"Math\",\"syllabus\":\"\",\"topics\":[{\"title\":\"Theme\",\"description\":\"Description\",\"references\":\"\"}]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(1));
        mvc.perform(get("/api/classrooms/2/subjects"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Math"));
    }
}
