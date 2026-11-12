package pulsoescolar_api.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import pulsoescolar_api.entity.user.SchoolUser;
import pulsoescolar_api.repository.user.UserRepository;

@Component
@RequiredArgsConstructor
public class SecurityContextCurrentUser implements CurrentUser {
    private final UserRepository users;

    @Override
    public SchoolUser get() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("É necessário estar autenticado.");
        }
        return users.findByEmail(authentication.getName())
                .filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> new AccessDeniedException("Usuário autenticado não encontrado."));
    }
}
