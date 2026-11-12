package pulsoescolar_api.dto.user;

import java.util.List;
import pulsoescolar_api.dto.classroom.ClassroomResponse;
import pulsoescolar_api.dto.school.SchoolSearchResponse.SchoolOption;

public record UserProfileResponse(UserResponse user, List<SchoolOption> schools,
        List<ClassroomResponse> classrooms, DeletionResponse deletionRequest) {}
