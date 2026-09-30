package pulsoescolar_api.controller.school;

import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pulsoescolar_api.dto.school.SchoolSearchResponse;
import pulsoescolar_api.service.school.SchoolSearchService;

@RestController
@RequestMapping("/api/schools/search")
@RequiredArgsConstructor
public class SchoolSearchController {
    private final SchoolSearchService service;

    @GetMapping
    public SchoolSearchResponse search(
            @RequestParam(required = false) @Pattern(regexp = "[0-9]{5}-?[0-9]{3}") String cep,
            @RequestParam(required = false) @Size(max = 100) String city,
            @RequestParam(required = false) @Pattern(regexp = "[A-Za-z]{2}") String state,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.search(cep, city, state, q, page, size);
    }
}
