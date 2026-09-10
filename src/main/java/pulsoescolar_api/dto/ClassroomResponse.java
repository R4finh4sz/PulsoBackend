package pulsoescolar_api.dto;

import java.util.List;

public record ClassroomResponse(Long id, String name, List<Long> teacherIds) {}
