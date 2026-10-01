package pulsoescolar_api.controller.user.student;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pulsoescolar_api.dto.user.*;
import pulsoescolar_api.service.user.student.StudentService;

import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {
    private final StudentService service;

    @PostMapping
    @ResponseStatus(CREATED)
    public RegistrationReceipt create(@Valid @RequestBody SelfRegistrationRequest request) {
        return service.create(request);
    }

    @GetMapping
    public UserPageResponse list(@RequestParam(required = false) String q,
            @RequestParam(required = false) @Min(1) Long classroomId,
            @RequestParam(required = false) Boolean unassigned,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(q, classroomId, unassigned, page, size);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return service.update(id, request);
    }

}
