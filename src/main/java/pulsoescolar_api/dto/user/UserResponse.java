package pulsoescolar_api.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;
import pulsoescolar_api.entity.user.Role;

public record UserResponse(Long id, String fullName, String ra, String email, Role role, Long classroomId, Long schoolId,
                           boolean schoolUnavailable,
                           @JsonInclude(JsonInclude.Include.NON_NULL) LocalDate birthDate,
                           @JsonInclude(JsonInclude.Include.NON_NULL) Boolean termsAccepted,
                           @JsonInclude(JsonInclude.Include.NON_NULL) List<String> termsAcceptedVersions,
                           @JsonInclude(JsonInclude.Include.NON_NULL) String status) {
    public UserResponse(Long id, String fullName, String ra, String email, Role role, Long classroomId, Long schoolId,
                        boolean schoolUnavailable) {
        this(id, fullName, ra, email, role, classroomId, schoolId, schoolUnavailable, null, null, null, null);
    }
}
