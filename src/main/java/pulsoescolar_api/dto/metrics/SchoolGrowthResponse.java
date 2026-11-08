package pulsoescolar_api.dto.metrics;

import java.math.BigDecimal;
import java.util.List;

public record SchoolGrowthResponse(Long schoolId, String schoolName, int year, List<Month> months,
        BigDecimal averageMonthlyStudents, BigDecimal averageMonthlyTeachers,
        long studentsWithoutRegistrationDate, long teachersWithoutRegistrationDate) {
    public record Month(int month, long students, long teachers, long cumulativeStudents, long cumulativeTeachers) {}
}
