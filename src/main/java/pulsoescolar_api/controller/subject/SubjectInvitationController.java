package pulsoescolar_api.controller.subject;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pulsoescolar_api.dto.subject.*;
import pulsoescolar_api.service.subject.SubjectInvitationService;
import static org.springframework.http.HttpStatus.CREATED;

@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class SubjectInvitationController {
    private final SubjectInvitationService service;

    @PostMapping("/classrooms/{classroomId}/subjects/{subjectId}/invitations")
    @ResponseStatus(CREATED)
    public SubjectInvitationResponse create(@PathVariable Long classroomId, @PathVariable Long subjectId,
                                             @Valid @RequestBody CreateSubjectInvitationRequest request) {
        return service.create(classroomId, subjectId, request);
    }

    @GetMapping("/subject-invitations")
    public List<SubjectInvitationResponse> mine() { return service.mine(); }

    @PatchMapping("/subject-invitations/{id}")
    public SubjectInvitationResponse respond(@PathVariable Long id, @Valid @RequestBody RespondSubjectInvitationRequest request) {
        return service.respond(id, request);
    }
}
