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
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class LoginController {
    private final LoginService loginService;
    private final ChangePasswordService changePasswordService;

    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest body) {
        changePasswordService.change(jwt, body);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(loginService.login(body));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        loginService.logout(jwt);
        return ResponseEntity.noContent().build();
    }
}
