package pulsoescolar_api.controller.user.coordinator;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pulsoescolar_api.dto.user.*;
import pulsoescolar_api.service.user.coordinator.CoordinatorService;

import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/api/coordinators")
@RequiredArgsConstructor
public class CoordinatorController {
    private final CoordinatorService service;

    @PostMapping
    @ResponseStatus(CREATED)
    public RegistrationReceipt create(@Valid @RequestBody SelfRegistrationRequest request) {
        return service.create(request);
    }

    @GetMapping
    public UserPageResponse list(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(q, page, size);
    }

}
