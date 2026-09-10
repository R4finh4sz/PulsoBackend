package pulsoescolar_api.controller;

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
import pulsoescolar_api.dto.NameRequest;
import pulsoescolar_api.dto.SubjectResponse;
import pulsoescolar_api.service.SubjectService;
import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/api/classrooms/{id}/subjects")
@RequiredArgsConstructor
public class SubjectController {
    private final SubjectService service;

    @PostMapping
    @ResponseStatus(CREATED)
    public SubjectResponse subject(@PathVariable Long id, @Valid @RequestBody NameRequest request) {
        return service.createSubject(id, request);
    }

    @GetMapping
    public List<SubjectResponse> subjects(@PathVariable Long id) {
        return service.subjects(id);
    }
}
