package pulsoescolar_api.service.user;

import java.util.ArrayList;
import java.util.Locale;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.user.*;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.mapper.user.UserMapper;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.security.CurrentUser;
import pulsoescolar_api.security.user.UserAccessPolicy;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserManagementService {
    private final UserRepository users;
    private final UserLookupService lookup;
    private final CurrentUser currentUser;
    private final UserAccessPolicy policy;
    private final UserMapper mapper;

    public UserPageResponse list(Role role, String q, Long classroomId, Boolean unassigned, int page, int size) {
        policy.requireManager(currentUser.get().getRole());
        Specification<SchoolUser> filter = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("role"), role));
            if (q != null && !q.isBlank()) {
                String term = "%" + q.strip().toLowerCase(Locale.ROOT)
                        .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("fullName")), term, '!'),
                        cb.like(cb.lower(root.get("ra")), term, '!')));
            }
            if (classroomId != null) {
                predicates.add(cb.equal(root.get("classroom").get("id"), classroomId));
            }
            if (unassigned != null) {
                predicates.add(unassigned ? cb.isNull(root.get("classroom")) : cb.isNotNull(root.get("classroom")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return UserPageResponse.from(users.findAll(filter,
                PageRequest.of(page, size, Sort.by("fullName").ascending().and(Sort.by("id"))))
                .map(mapper::toResponse));
    }

    public UserResponse get(Long id, Role role) {
        policy.requireManager(currentUser.get().getRole());
        return mapper.toResponse(lookup.findByRole(id, role));
    }

    @Transactional
    public UserResponse update(Long id, Role role, UpdateUserRequest request) {
        policy.requireManager(currentUser.get().getRole());
        var user = lookup.findByRole(id, role);
        if (request.fullName() != null) user.setFullName(request.fullName().strip());
        if (request.ra() != null) user.setRa(request.ra().strip());
        if (request.email() != null) user.setEmail(request.email().strip().toLowerCase(Locale.ROOT));
        return mapper.toResponse(users.saveAndFlush(user));
    }
}
