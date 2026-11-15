package pulsoescolar_api.controller.guardian;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import pulsoescolar_api.dto.guardian.GuardianDtos.*;
import pulsoescolar_api.entity.guardian.GuardianStatus;
import pulsoescolar_api.service.guardian.GuardianConfirmationService;

@RestController
@RequestMapping("/api/guardian-confirmations")
@RequiredArgsConstructor
@Validated
public class GuardianConfirmationController {
    private final GuardianConfirmationService service;

    @GetMapping
    public GuardianPageResponse list(
            @RequestParam(defaultValue = "AWAITING_SCHOOL_CHECK") GuardianStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(status, page, size);
    }

    @PostMapping("/{id}/verify")
    public GuardianConfirmationResponse verify(@PathVariable Long id, @Valid @RequestBody VerifyGuardianRequest input) {
        return service.verify(id, input);
    }

    @PostMapping("/{id}/request-correction")
    public GuardianConfirmationResponse requestCorrection(@PathVariable Long id,
            @Valid @RequestBody RequestGuardianCorrectionRequest input) {
        return service.requestCorrection(id, input);
    }

    @PostMapping("/{id}/resend")
    public GuardianConfirmationResponse resend(@PathVariable Long id) {
        return service.resend(id);
    }
}
