package pulsoescolar_api.service.activity;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.activity.StudentRankingResponse;
import pulsoescolar_api.exception.common.ResourceNotFoundException;
import pulsoescolar_api.repository.activity.QuizSubmissionRepository;
import pulsoescolar_api.repository.school.SchoolRepository;

@Service @RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentRankingService {
    private final QuizSubmissionRepository submissions;
    private final SchoolRepository schools;
    private final SchoolBimesterCalendar calendar;
    private final StudentRankingCalculator calculator;

    public List<StudentRankingResponse> ranking(int year, int bimester, Long schoolId) {
        var period = calendar.period(year, bimester);
        if (schoolId != null && !schools.existsById(schoolId)) {
            throw new ResourceNotFoundException("Escola não encontrada.");
        }
        return calculator.calculate(submissions.rankStudents(period.start(), period.endExclusive(), schoolId));
    }
}
