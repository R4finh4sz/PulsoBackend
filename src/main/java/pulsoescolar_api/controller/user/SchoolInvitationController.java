package pulsoescolar_api.controller.user;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pulsoescolar_api.dto.user.*;
import pulsoescolar_api.service.user.SchoolInvitationService;

@RestController
@RequestMapping("/api/me/school-invitations")
@RequiredArgsConstructor
public class SchoolInvitationController {
    private final SchoolInvitationService service;

    @GetMapping
    public List<SchoolInvitationResponse> mine() { return service.mine(); }

    @PatchMapping("/{id}")
    public SchoolInvitationResponse respond(@PathVariable Long id,
            @Valid @RequestBody RespondSchoolInvitationRequest input) {
        return service.respond(id, input);
    }
}
