package pulsoescolar_api.dto.activity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TeacherReportResponse(Instant generatedAt, Summary summary, List<ClassroomReport> classrooms) {
    public record Summary(BigDecimal averageGrade, Long expectedSubmissions, long submittedCount,
            BigDecimal completionPercentage, Long studentsWithOverdue, long classroomCount, long activityCount,
            boolean recipientHistoryComplete) {}
    public record ClassroomReport(Long classroomId, String classroomName, Long schoolId, String schoolName,
            Long studentCount, long activityCount, BigDecimal averageGrade, Long expectedSubmissions,
            long submittedCount, BigDecimal completionPercentage, Long studentsWithOverdue,
            boolean recipientHistoryComplete) {}
}
