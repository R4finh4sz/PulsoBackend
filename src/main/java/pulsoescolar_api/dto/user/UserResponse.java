package pulsoescolar_api.dto.user;

import pulsoescolar_api.entity.user.Role;

public record UserResponse(Long id, String fullName, String ra, String email, Role role, Long classroomId) {}
