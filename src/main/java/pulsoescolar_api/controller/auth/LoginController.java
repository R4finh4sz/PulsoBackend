package pulsoescolar_api.controller.auth;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pulsoescolar_api.dto.auth.LoginRequest;
import pulsoescolar_api.dto.auth.LoginResponse;
import pulsoescolar_api.service.auth.LoginService;
import pulsoescolar_api.service.auth.ChangePasswordService;
import pulsoescolar_api.dto.auth.ChangePasswordRequest;
import pulsoescolar_api.service.audit.AuthAuditService;
import pulsoescolar_api.exception.auth.*;
import static pulsoescolar_api.service.audit.AuthAuditEvent.*;
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class LoginController {
    private final LoginService loginService;
    private final ChangePasswordService changePasswordService;
    private final pulsoescolar_api.service.auth.TwoFactorService twoFactor;
    private final AuthAuditService audit;

    @PatchMapping("/2fa")
    public ResponseEntity<Void> updateTwoFactor(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody pulsoescolar_api.dto.auth.TwoFactorSettingsRequest body) {
        twoFactor.updateSettings(jwt, body);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<Void> verify(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody pulsoescolar_api.dto.auth.VerifyCodeRequest body) {
        try {
            twoFactor.verify(jwt, body.code());
        } catch (InvalidVerificationCodeException | VerificationCodeUnavailableException | SessionUnavailableException
                 | VerificationAlreadyCompletedException ex) {
            audit.recordFailure(TWO_FACTOR_FAILED, Long.valueOf(jwt.getSubject()));
            throw ex;
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/2fa/resend")
    public ResponseEntity<pulsoescolar_api.dto.auth.TwoFactorResponse> resend(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(twoFactor.resend(jwt));
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest body) {
        changePasswordService.change(jwt, body);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest body) {
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(loginService.login(body));
        } catch (org.springframework.security.core.AuthenticationException | VerificationCooldownException ex) {
            // A transação da tentativa já terminou; a falha não desaparece no rollback.
            audit.recordFailure(LOGIN_FAILED, null);
            throw ex;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        loginService.logout(jwt);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody pulsoescolar_api.dto.auth.RefreshTokenRequest body) {
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(loginService.refresh(body.refreshToken()));
        } catch (InvalidRefreshTokenException ex) {
            audit.recordFailure(TOKEN_REFRESH_FAILED, null);
            throw ex;
        }
    }
}
