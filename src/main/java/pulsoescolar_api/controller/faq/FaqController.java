package pulsoescolar_api.controller.faq;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pulsoescolar_api.dto.faq.*;
import pulsoescolar_api.service.faq.FaqService;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/faqs")
@RequiredArgsConstructor
public class FaqController {
    private final FaqService service;

    @GetMapping
    public List<FaqResponse> list() { return service.list(); }

    @PostMapping
    @ResponseStatus(CREATED)
    public FaqResponse create(@Valid @RequestBody FaqRequest request) { return service.create(request); }

    @PutMapping("/{id}")
    public FaqResponse update(@PathVariable Long id, @Valid @RequestBody FaqRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}
