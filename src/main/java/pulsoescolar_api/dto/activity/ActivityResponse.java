package pulsoescolar_api.dto.activity;

import java.time.Instant;

public record ActivityResponse(Long id, Long subjectId, Long classroomId, Long schoolId,
        int questionCount, Instant createdAt, Instant closesAt, ActivityStatus status, int year) {}
