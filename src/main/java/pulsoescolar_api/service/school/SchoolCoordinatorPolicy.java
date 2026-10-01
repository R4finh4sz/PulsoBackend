package pulsoescolar_api.service.school;

import pulsoescolar_api.exception.ResourceNotFoundException;
import pulsoescolar_api.exception.SchoolAlreadyHasCoordinatorException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.repository.school.SchoolRepository;
import pulsoescolar_api.repository.user.UserRepository;

@Component
@RequiredArgsConstructor
public class SchoolCoordinatorPolicy {
    private final SchoolRepository schools;
    private final UserRepository users;

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireVacancy(Long schoolId) {
        schools.findForUpdate(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Escola não encontrada."));
        if (users.existsBySchoolIdAndRoleAndDeletedAtIsNull(schoolId, Role.PEDAGOGICAL_COORDINATOR)) {
            throw new SchoolAlreadyHasCoordinatorException();
        }
    }
}
