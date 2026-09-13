package pulsoescolar_api.service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pulsoescolar_api.dto.UserResponse;
import pulsoescolar_api.mapper.UserMapper;
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
