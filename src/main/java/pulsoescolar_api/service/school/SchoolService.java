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
    private final SchoolRepository schools;
    private final CurrentUser currentUser;

    private void requireAdmin() {
        if (currentUser.get().getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Esta operação exige acesso de administrador.");
        }
    }

    @Transactional
    public SchoolResponse create(CreateSchoolRequest request) {
        requireAdmin();
        var school = new School();
        school.setNome(request.nome().strip());
        school.setCnpj(request.cnpj().replaceAll("[./-]", ""));
        school.setLogradouro(request.logradouro().strip());
        school.setBairro(request.bairro().strip());
        school.setCidade(request.cidade().strip());
        return response(schools.saveAndFlush(school));
    }

    public List<SchoolResponse> list() {
        requireAdmin();
        return schools.findAll(Sort.by("nome").ascending().and(Sort.by("id"))).stream()
                .map(this::response).toList();
    }

    private SchoolResponse response(School school) {
        return new SchoolResponse(school.getId(), school.getNome(), school.getCnpj(),
                school.getLogradouro(), school.getBairro(), school.getCidade());
    }
}
