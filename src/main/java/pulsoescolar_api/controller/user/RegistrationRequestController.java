package pulsoescolar_api.controller.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import pulsoescolar_api.dto.user.*;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.service.user.RegistrationReviewService;

@RestController
@RequestMapping("/api/registration-requests")
@RequiredArgsConstructor
public class RegistrationRequestController {
    private final RegistrationReviewService service;

    @GetMapping
    public RegistrationPageResponse list(
            @RequestParam(defaultValue = "PENDING") RegistrationStatus status,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) @Positive Long schoolId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(status, role, schoolId, page, size);
    }

    @PatchMapping("/{id}")
    public RegistrationResponse review(@PathVariable Long id, @Valid @RequestBody ReviewRegistrationRequest input) {
        return service.review(id, input);
    }

    @GetMapping(value = "/{id}/photo", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.photo(id));
    }
}
