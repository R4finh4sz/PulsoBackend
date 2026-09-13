package pulsoescolar_api.service.user;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pulsoescolar_api.dto.user.UserResponse;
import pulsoescolar_api.mapper.user.UserMapper;
import pulsoescolar_api.security.CurrentUser;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {
    private final CurrentUser currentUser;
    private final UserMapper mapper;

    public UserResponse me() {
        return mapper.toResponse(currentUser.get());
    }
}
