package pulsoescolar_api.dto.subject;



public record SubjectResponse(Long id, String name, Long classroomId, Long teacherId,
        String syllabus, java.util.List<CreateSubjectRequest.Topic> topics, java.util.List<Long> teacherIds) {}
