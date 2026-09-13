package pulsoescolar_api.service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pulsoescolar_api.entity.Role;
import pulsoescolar_api.entity.SchoolUser;
import pulsoescolar_api.exception.InvalidUserRoleException;
import pulsoescolar_api.exception.ResourceNotFoundException;
import pulsoescolar_api.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserLookupService {
    private final UserRepository users;

    public SchoolUser findByRole(Long id, Role role) {
        var user = users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() != role) {
            throw new InvalidUserRoleException("Invalid user role");
        }
        return user;
    }
}
