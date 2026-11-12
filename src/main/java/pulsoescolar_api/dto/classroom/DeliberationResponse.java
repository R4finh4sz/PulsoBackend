package pulsoescolar_api.dto.classroom;

import java.time.Instant;
import java.util.List;
import pulsoescolar_api.entity.classroom.ClassroomDeliberation;
import pulsoescolar_api.entity.classroom.DeliberationAction;
import pulsoescolar_api.entity.classroom.DeliberationResult;

public record DeliberationResponse(Long id, Long classroomId, DeliberationAction action, Long performedById,
                                   Instant performedAt, List<Entry> students) {
    public record Entry(Long studentId, DeliberationResult result, Long destinationClassroomId) {}

    public static DeliberationResponse from(ClassroomDeliberation deliberation) {
        return new DeliberationResponse(deliberation.getId(), deliberation.getClassroom().getId(),
                deliberation.getAction(), deliberation.getPerformedBy().getId(), deliberation.getPerformedAt(),
                deliberation.getEntries().stream().map(entry -> new Entry(entry.getStudent().getId(), entry.getResult(),
                        entry.getDestination() == null ? null : entry.getDestination().getId())).toList());
    }
}
