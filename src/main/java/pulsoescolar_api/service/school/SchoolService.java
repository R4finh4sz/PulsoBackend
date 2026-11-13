package pulsoescolar_api.service.school;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.school.*;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.repository.school.SchoolRepository;
import pulsoescolar_api.security.CurrentUser;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolService {
    private final pulsoescolar_api.service.audit.AuditService audit;
    private final SchoolRepository schools;
    private final CurrentUser currentUser;
    private final SchoolAddressValidator addressValidator;
    private final pulsoescolar_api.repository.user.UserRepository users;
    private final jakarta.persistence.EntityManager entityManager;
    private final pulsoescolar_api.mapper.user.UserMapper userMapper;

    private void requireAdmin() {
        if (currentUser.get().getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Esta operação exige acesso de administrador.");
        }
    }

    @Transactional
    public SchoolResponse create(CreateSchoolRequest request) {
        requireAdmin();
        addressValidator.validate(request.cep(), request.uf(), request.cidade(), request.bairro(), request.logradouro());
        var school = new School();
        school.setNome(request.nome().strip());
        school.setLogradouro(request.logradouro().strip());
        school.setBairro(request.bairro().strip());
        school.setCidade(request.cidade().strip());
        school.setCep(request.cep().replace("-", ""));
        school.setUf(request.uf().name());
        var result = response(schools.saveAndFlush(school));
        audit.record(pulsoescolar_api.service.audit.AuditEvent.SCHOOL_CREATED);
        return result;
    }

    public List<SchoolResponse> list() {
        requireAdmin();
        var result = schools.findByActiveTrue(Sort.by("nome").ascending().and(Sort.by("id")));
        if (result.isEmpty()) return List.of();
        var coordinators = users.findByRoleAndDeletedAtIsNullAndSchoolIdIn(Role.PEDAGOGICAL_COORDINATOR,
                result.stream().map(School::getId).toList()).stream().collect(java.util.stream.Collectors.toMap(
                        u -> u.getEnrollmentSchool().getId(),
                        u -> new SchoolResponse.Coordinator(u.getId(), u.getFullName(), u.getEmail())));
        return result.stream().map(s -> response(s, coordinators.get(s.getId()))).toList();
    }

    @Transactional
    public SchoolResponse updateLocation(Long id, UpdateSchoolLocationRequest request) {
        requireAdmin();
        var school = schools.findForUpdate(id).orElseThrow(() ->
                new pulsoescolar_api.exception.common.ResourceNotFoundException("Escola não encontrada."));
        school.requireActive();
        addressValidator.validate(request.cep(), request.uf(), school.getCidade(), school.getBairro(), school.getLogradouro());
        school.setCep(request.cep().replace("-", ""));
        school.setUf(request.uf().name());
        return response(school);
    }

    @Transactional
    public void deactivate(Long id) {
        requireAdmin();
        var school = schools.findForUpdate(id).orElseThrow(() ->
                new pulsoescolar_api.exception.common.ResourceNotFoundException("Escola não encontrada."));
        school.setActive(false);
    }

    @Transactional
    public pulsoescolar_api.dto.user.UserResponse assignUser(Long id, Long userId) {
        requireAdmin();
        var school = schools.findForUpdate(id).orElseThrow(() ->
                new pulsoescolar_api.exception.common.ResourceNotFoundException("Escola não encontrada."));
        school.requireActive();
        var user = users.findById(userId).orElseThrow(() ->
                new pulsoescolar_api.exception.common.ResourceNotFoundException("Usuário não encontrado."));
        entityManager.refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (user.getDeletedAt() != null || (user.getRole() != Role.STUDENT && user.getRole() != Role.TEACHER)) {
            throw new pulsoescolar_api.exception.common.BusinessValidationException("Informe um aluno ou professor ativo.");
        }
        if (user.getRole() == Role.STUDENT) {
            if (user.getSchool() != null && !user.getSchool().getId().equals(id)) {
                throw new pulsoescolar_api.exception.common.BusinessConflictException("Use a transferência para alunos com escola ativa.");
            }
            if (user.getSchool() == null) user.setClassroom(null);
        }
        user.setSchool(school);
        users.flush();
        return userMapper.toResponse(user);
    }

    @Transactional
    public SchoolResponse updateCoordinator(Long id, UpdateSchoolCoordinatorRequest request) {
        requireAdmin();
        var school = schools.findForUpdate(id).orElseThrow(() ->
                new pulsoescolar_api.exception.common.ResourceNotFoundException("Escola não encontrada."));
        var coordinator = users.findById(request.coordinatorId()).orElseThrow(() ->
                new pulsoescolar_api.exception.common.ResourceNotFoundException("Coordenador não encontrado."));
        entityManager.refresh(coordinator, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        school.requireActive();
        if (coordinator.getRole() != Role.PEDAGOGICAL_COORDINATOR || coordinator.getDeletedAt() != null) {
            throw new pulsoescolar_api.exception.common.BusinessValidationException("Informe um coordenador ativo.");
        }
        if (coordinator.getEnrollmentSchool() != null && coordinator.getEnrollmentSchool().isActive()
                && !coordinator.getEnrollmentSchool().getId().equals(id)) {
            throw new pulsoescolar_api.exception.common.BusinessConflictException(
                    "O coordenador já está vinculado a outra escola.");
        }
        for (var previous : users.findByRoleAndDeletedAtIsNullAndSchoolIdIn(
                Role.PEDAGOGICAL_COORDINATOR, List.of(id))) {
            if (previous.getId().equals(coordinator.getId())) continue;
            previous.getSchools().removeIf(value -> value.getId().equals(id));
            previous.setSchool(null);
        }
        // Release the unique coordinator slot before assigning its replacement.
        users.flush();
        coordinator.setSchool(school);
        users.flush();
        return response(school);
    }

    private SchoolResponse response(School school) {
        var coordinator = users.findByRoleAndDeletedAtIsNullAndSchoolIdIn(Role.PEDAGOGICAL_COORDINATOR,
                List.of(school.getId())).stream().findFirst()
                .map(u -> new SchoolResponse.Coordinator(u.getId(), u.getFullName(), u.getEmail())).orElse(null);
        return response(school, coordinator);
    }

    private SchoolResponse response(School school, SchoolResponse.Coordinator coordinator) {
        return new SchoolResponse(school.getId(), school.getNome(),
                school.getLogradouro(), school.getBairro(), school.getCidade(), school.getCep(), school.getUf(), coordinator);
    }
}
