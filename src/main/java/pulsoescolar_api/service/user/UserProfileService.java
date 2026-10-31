package pulsoescolar_api.service.user;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pulsoescolar_api.dto.user.UserResponse;
import pulsoescolar_api.mapper.user.UserMapper;
import pulsoescolar_api.security.CurrentUser;
import java.util.List;
import pulsoescolar_api.dto.user.UserProfileResponse;
import pulsoescolar_api.dto.user.DeletionResponse;
import pulsoescolar_api.dto.school.SchoolSearchResponse.SchoolOption;
import pulsoescolar_api.entity.classroom.Classroom;
import pulsoescolar_api.mapper.classroom.ClassroomMapper;
import pulsoescolar_api.repository.classroom.ClassroomRepository;
import pulsoescolar_api.repository.user.AccountDeletionRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {
    private final CurrentUser currentUser;
    private final UserMapper mapper;
    private final ClassroomRepository classrooms;
    private final ClassroomMapper classroomMapper;
    private final AccountDeletionRepository deletionRequests;

    public UserResponse me() {
        return mapper.toResponse(currentUser.get());
    }

    public UserProfileResponse profile() {
        var user = currentUser.get();
        var rooms = switch (user.getRole()) {
            case TEACHER -> classrooms.findByTeachersId(user.getId());
            case STUDENT -> user.getClassroom() == null ? List.<Classroom>of() : List.of(user.getClassroom());
            case PEDAGOGICAL_COORDINATOR -> user.getSchool() == null
                    ? List.<Classroom>of() : classrooms.findBySchoolId(user.getSchool().getId());
            case ADMIN -> List.<Classroom>of();
        };
        return new UserProfileResponse(mapper.toResponse(user),
                user.getSchool() == null ? List.of() : List.of(SchoolOption.from(user.getSchool())),
                rooms.stream().map(classroomMapper::toResponse).toList(),
                deletionRequests.findFirstByRequesterIdOrderByRequestedAtDescIdDesc(user.getId())
                        .map(DeletionResponse::from).orElse(null));
    }

    public byte[] photo() {
        var photo = currentUser.get().getProfilePhoto();
        if (photo == null) throw new pulsoescolar_api.exception.ResourceNotFoundException("Foto não encontrada.");
        return photo;
    }
}
