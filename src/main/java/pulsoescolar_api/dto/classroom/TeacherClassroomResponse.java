package pulsoescolar_api.dto.classroom;

import java.util.List;

public record TeacherClassroomResponse(Long id, String name, String identifier, List<Long> teacherIds,
        Long schoolId, String schoolName) {}
