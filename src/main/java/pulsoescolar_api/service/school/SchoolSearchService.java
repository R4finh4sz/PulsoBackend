package pulsoescolar_api.service.school;

import java.util.ArrayList;
import java.util.Locale;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.school.SchoolSearchResponse;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.repository.school.SchoolRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolSearchService {
    private final SchoolRepository schools;

    public SchoolSearchResponse search(String cep, String city, String state, String q, int page, int size) {
        Specification<School> filter = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (cep != null) predicates.add(cb.equal(root.get("cep"), cep.replace("-", "")));
            if (city != null && !city.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("cidade")), city.strip().toLowerCase(Locale.ROOT)));
            }
            if (state != null) predicates.add(cb.equal(root.get("uf"), state.toUpperCase(Locale.ROOT)));
            if (q != null && !q.isBlank()) {
                String term = q.strip().toLowerCase(Locale.ROOT).replace("!", "!!")
                        .replace("%", "!%").replace("_", "!_");
                predicates.add(cb.like(cb.lower(root.get("nome")), "%" + term + "%", '!'));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return SchoolSearchResponse.from(schools.findAll(filter,
                PageRequest.of(page, size, Sort.by("nome").ascending().and(Sort.by("id")))));
    }
}
