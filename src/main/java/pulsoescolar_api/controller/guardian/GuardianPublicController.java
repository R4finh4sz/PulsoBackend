package pulsoescolar_api.controller.guardian;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pulsoescolar_api.dto.guardian.GuardianDtos.*;
import pulsoescolar_api.service.guardian.GuardianConfirmationService;
import pulsoescolar_api.service.guardian.GuardianInvitationService;

/** Public routes authenticated only by the single-use link token sent by e-mail. */
@RestController
@RequiredArgsConstructor
public class GuardianPublicController {
    private final GuardianInvitationService invitations;
    private final GuardianConfirmationService confirmations;

    @GetMapping("/api/guardian-invitations/{token}")
    public ResponseEntity<GuardianInvitationView> invitation(@PathVariable String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(invitations.view(token));
    }

    @PostMapping("/api/guardian-invitations/{token}/decision")
    public ResponseEntity<GuardianDecisionReceipt> decide(@PathVariable String token,
            @Valid @RequestBody GuardianDecisionRequest input) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(invitations.decide(token, input));
    }

    @GetMapping("/api/guardian-corrections/{token}")
    public ResponseEntity<GuardianCorrectionView> correction(@PathVariable String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(confirmations.correctionView(token));
    }

    @PostMapping("/api/guardian-corrections/{token}")
    public ResponseEntity<GuardianCorrectionReceipt> correct(@PathVariable String token,
            @Valid @RequestBody CorrectGuardianRequest input) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(confirmations.applyCorrection(token, input));
    }
}
