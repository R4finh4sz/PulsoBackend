package pulsoescolar_api.service.user;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.entity.user.SchoolUser;
import pulsoescolar_api.exception.InvalidUserRoleException;
import pulsoescolar_api.exception.ResourceNotFoundException;
import pulsoescolar_api.repository.user.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserLookupService {
    private final UserRepository users;

    public SchoolUser findByRole(Long id, Role role) {
        var user = users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        if (user.getRole() != role) {
            throw new InvalidUserRoleException("O usuário não possui o perfil necessário para esta operação.");
        }
        return user;
    }
}
