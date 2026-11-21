package pulsoescolar_api.controller.guardian;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pulsoescolar_api.dto.guardian.GuardianDtos.GuardianRenewalStatus;
import pulsoescolar_api.service.guardian.GuardianConfirmationService;

@RestController
@RequestMapping("/api/me/guardian-renewal")
@RequiredArgsConstructor
public class GuardianRenewalController {
    private final GuardianConfirmationService guardians;

    @GetMapping
    public ResponseEntity<GuardianRenewalStatus> status(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(guardians.renewalStatus(UUID.fromString(jwt.getId())));
    }

    @PostMapping("/send")
    public ResponseEntity<GuardianRenewalStatus> send(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                .body(guardians.sendRenewal(UUID.fromString(jwt.getId())));
    }
}
