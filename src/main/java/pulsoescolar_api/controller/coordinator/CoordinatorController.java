package pulsoescolar_api.controller.coordinator;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pulsoescolar_api.dto.user.*;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.service.user.UserManagementService;

@RestController
@RequestMapping("/api/coordinators")
@RequiredArgsConstructor
public class CoordinatorController {
    private final UserManagementService service;

    @GetMapping
    public UserPageResponse list(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(Role.PEDAGOGICAL_COORDINATOR, q, null, null, page, size);
    }

}
