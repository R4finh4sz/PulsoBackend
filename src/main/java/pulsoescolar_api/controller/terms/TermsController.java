package pulsoescolar_api.controller.terms;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pulsoescolar_api.dto.terms.*;
import pulsoescolar_api.service.terms.TermsService;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/terms")
@RequiredArgsConstructor
public class TermsController {
    private final TermsService service;
    @GetMapping
    public TermsResponse get() { return service.get(); }
    @PostMapping
    @ResponseStatus(CREATED)
    public TermsResponse create(@Valid @RequestBody TermsRequest request) {
        return service.publish(request, true);
    }
    @PutMapping
    public TermsResponse update(@Valid @RequestBody TermsRequest request) {
        return service.publish(request, false);
    }
    @PostMapping("/accept")
    @ResponseStatus(NO_CONTENT)
    public void accept(@Valid @RequestBody AcceptTermsRequest request) { service.accept(request); }
}
