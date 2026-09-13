package pulsoescolar_api.controller.school;

import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pulsoescolar_api.dto.school.*;
import pulsoescolar_api.service.school.SchoolService;
import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/api/schools")
@RequiredArgsConstructor
public class SchoolController {
    private final SchoolService service;

    @PostMapping
    @ResponseStatus(CREATED)
    public SchoolResponse create(@Valid @RequestBody CreateSchoolRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<SchoolResponse> list() {
        return service.list();
    }
}
