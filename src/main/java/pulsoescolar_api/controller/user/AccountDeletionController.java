package pulsoescolar_api.controller.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pulsoescolar_api.dto.user.*;
import pulsoescolar_api.entity.user.DeletionStatus;
import pulsoescolar_api.service.user.AccountDeletionService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AccountDeletionController {
    private final AccountDeletionService service;

    @PostMapping("/me/deletion-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public DeletionResponse create(@Valid @RequestBody CreateDeletionRequest request) {
        return service.create(request);
    }

    @GetMapping("/me/deletion-requests")
    public List<DeletionResponse> mine() { return service.mine(); }

    @GetMapping("/account-deletion-requests")
    public DeletionPageResponse list(@RequestParam(defaultValue = "PENDING") DeletionStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(status, page, size);
    }

    @PatchMapping("/account-deletion-requests/{id}")
    public DeletionResponse review(@PathVariable Long id, @Valid @RequestBody ReviewDeletionRequest request) {
        return service.review(id, request);
    }
}
