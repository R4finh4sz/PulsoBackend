package pulsoescolar_api.controller.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pulsoescolar_api.dto.user.UserExportResponse;
import pulsoescolar_api.service.user.UserExportService;

@RestController
@RequiredArgsConstructor
public class UserExportController {
    private final UserExportService service;

    @GetMapping(value = "/api/me/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserExportResponse> export() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pulso-dados.json\"")
                .body(service.export());
    }
}
