package pulsoescolar_api.dto;

import pulsoescolar_api.entity.Role;

public record UserResponse(Long id, String fullName, String ra, String email, Role role, Long classroomId) {}
