package pulsoescolar_api.controller.agreement;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pulsoescolar_api.dto.agreement.DataAgreementDtos.*;
import pulsoescolar_api.service.agreement.SchoolDataAgreementService;

@RestController
@RequestMapping("/api/schools/me/data-agreement")
@RequiredArgsConstructor
public class SchoolDataAgreementController {
    private final SchoolDataAgreementService service;

    @GetMapping
    public Response current() { return service.current(); }

    @PostMapping("/accept")
    public Response accept(@Valid @RequestBody AcceptRequest request, HttpServletRequest http) {
        return service.accept(request, http.getRemoteAddr());
    }
}
