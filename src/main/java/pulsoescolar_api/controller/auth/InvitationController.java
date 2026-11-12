package pulsoescolar_api.controller.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pulsoescolar_api.dto.user.*;
import pulsoescolar_api.service.user.InvitationService;

@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
public class InvitationController {
    private final InvitationService service;

    @PostMapping("/coordinators")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationReceipt coordinator(@Valid @RequestBody CreateCoordinatorInvitationRequest input) {
        return service.createCoordinator(input);
    }

    @PostMapping("/teachers")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationReceipt teacher(@Valid @RequestBody CreateTeacherInvitationRequest input) {
        return service.createTeacher(input);
    }

    @GetMapping("/{token}")
    public InvitationDetails open(@PathVariable String token) {
        return service.open(token);
    }

    @PostMapping("/{token}/verify")
    public InvitationDetails verify(@PathVariable String token, @Valid @RequestBody VerifyInvitationRequest input) {
        return service.verify(token, input);
    }

    @PostMapping("/{token}/resend")
    public InvitationDetails resend(@PathVariable String token) {
        return service.resend(token);
    }

    @PostMapping("/{token}/complete")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationReceipt complete(@PathVariable String token, @Valid @RequestBody CompleteInvitationRequest input) {
        return service.complete(token, input);
    }
}
