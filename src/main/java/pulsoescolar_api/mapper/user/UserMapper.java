package pulsoescolar_api.mapper.user;

import org.springframework.stereotype.Component;
import pulsoescolar_api.dto.user.UserResponse;
import pulsoescolar_api.entity.user.SchoolUser;

@Component
public class UserMapper {
    public UserResponse toResponse(SchoolUser user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getRa(), user.getEmail(),
                user.getRole(), user.getActiveClassroom() == null ? null : user.getActiveClassroom().getId(),
                user.getSchool() == null ? null : user.getSchool().getId(), user.isSchoolUnavailable());
    }

    /** Resposta do próprio usuário: inclui a data de nascimento, usada para escolher os termos aplicáveis. */
    public UserResponse toSelfResponse(SchoolUser user) {
        var base = toResponse(user);
        return new UserResponse(base.id(), base.fullName(), base.ra(), base.email(), base.role(), base.classroomId(),
                base.schoolId(), base.schoolUnavailable(), user.getBirthDate(),
                user.getRole() == pulsoescolar_api.entity.user.Role.ADMIN || user.isTermsAccepted(),
                user.getTermsAcceptedVersions());
    }
}
