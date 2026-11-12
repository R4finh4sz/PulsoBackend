package pulsoescolar_api.dto.user;

import pulsoescolar_api.entity.user.RegistrationStatus;

public record RegistrationReceipt(Long id, RegistrationStatus status) {}
