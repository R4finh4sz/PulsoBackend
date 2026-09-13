package pulsoescolar_api.mapper.user;

import org.springframework.stereotype.Component;
import pulsoescolar_api.dto.user.UserResponse;
import pulsoescolar_api.entity.user.SchoolUser;

@Component
public class UserMapper {
    public UserResponse toResponse(SchoolUser user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getRa(), user.getEmail(),
                user.getRole(), user.getClassroom() == null ? null : user.getClassroom().getId());
    }
}
