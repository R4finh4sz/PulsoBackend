package pulsoescolar_api.dto.user;

import jakarta.validation.constraints.*;
import pulsoescolar_api.entity.user.RegistrationStatus;

/**
 * {@code identityChecked} is mandatory to approve a student: the reviewer states that name, RA and birth date
 * were checked against the school records.
 */
public record ReviewRegistrationRequest(@NotNull RegistrationStatus status, @Size(max = 2000) String reason,
                                        RegistrationRequestType type, @Positive Long classroomId,
                                        Boolean identityChecked) {
    public ReviewRegistrationRequest(RegistrationStatus status, String reason, RegistrationRequestType type, Long classroomId) {
        this(status, reason, type, classroomId, null);
    }
}
