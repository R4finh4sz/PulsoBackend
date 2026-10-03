package pulsoescolar_api.controller.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pulsoescolar_api.dto.auth.*;
import pulsoescolar_api.service.auth.PasswordRecoveryService;

@RestController
@RequestMapping({"/api/auth/password-recovery", "/api/auth/password-reset"})
@RequiredArgsConstructor
public class PasswordRecoveryController {
    private final PasswordRecoveryService recovery;

    @PostMapping("/request")
    public ResponseEntity<RecoveryRequestedResponse> request(@Valid @RequestBody PasswordRecoveryRequest input) {
        recovery.request(input.email());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(new RecoveryRequestedResponse(
                "Se houver uma conta ativa para este e-mail, enviaremos um código de verificação. Aguarde 3 minutos antes de solicitar outro código."));
    }

    @PostMapping("/verify")
    public ResponseEntity<RecoveryVerificationResponse> verify(@Valid @RequestBody VerifyRecoveryCodeRequest input) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(recovery.verify(input));
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest input) {
        recovery.reset(input);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    public record RecoveryRequestedResponse(String message) {}
}
