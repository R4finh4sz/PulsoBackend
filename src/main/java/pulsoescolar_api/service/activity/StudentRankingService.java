package pulsoescolar_api.service.activity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.text.Collator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.activity.StudentRankingResponse;
import pulsoescolar_api.exception.common.BusinessValidationException;
import pulsoescolar_api.exception.common.ResourceNotFoundException;
import pulsoescolar_api.repository.activity.QuizSubmissionRepository;
import pulsoescolar_api.repository.school.SchoolRepository;

@Service @RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentRankingService {
    private final QuizSubmissionRepository submissions;
    private final SchoolRepository schools;

    public List<StudentRankingResponse> ranking(int year, int bimester, Long schoolId) {
        if (year != 2026 || bimester < 1 || bimester > 4) {
            throw new BusinessValidationException("Informe o ano 2026 e um bimestre de 1 a 4.");
        }
        if (schoolId != null && !schools.existsById(schoolId)) {
            throw new ResourceNotFoundException("Escola não encontrada.");
        }
        // Calendário informado para 2026; novos anos precisam de suas próprias datas letivas.
        var starts = List.of(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 10, 1));
        var ends = List.of(LocalDate.of(2026, 4, 30), LocalDate.of(2026, 7, 30),
                LocalDate.of(2026, 10, 30), LocalDate.of(2026, 12, 30));
        var zone = ZoneId.of("America/Sao_Paulo");
        Comparator<QuizSubmissionRepository.StudentRanking> performance = (left, right) ->
                BigDecimal.valueOf(right.getCorrectAnswers()).multiply(BigDecimal.valueOf(left.getTotalQuestions()))
                        .compareTo(BigDecimal.valueOf(left.getCorrectAnswers()).multiply(BigDecimal.valueOf(right.getTotalQuestions())));
        performance = performance.thenComparing(QuizSubmissionRepository.StudentRanking::getCorrectAnswers, Comparator.reverseOrder())
                .thenComparing(QuizSubmissionRepository.StudentRanking::getTotalActivities, Comparator.reverseOrder());
        var alphabetical = Collator.getInstance(Locale.forLanguageTag("pt-BR"));
        alphabetical.setStrength(Collator.PRIMARY);
        var rows = submissions.rankStudents(starts.get(bimester - 1).atStartOfDay(zone).toInstant(),
                ends.get(bimester - 1).plusDays(1).atStartOfDay(zone).toInstant(), schoolId).stream()
                .sorted(performance.thenComparing(QuizSubmissionRepository.StudentRanking::getStudentName, alphabetical)
                        .thenComparing(QuizSubmissionRepository.StudentRanking::getStudentId)).toList();
        var result = new ArrayList<StudentRankingResponse>();
        int position = 0;
        QuizSubmissionRepository.StudentRanking previous = null;
        for (var row : rows) {
            if (previous == null || performance.compare(previous, row) != 0) position = result.size() + 1;
            result.add(new StudentRankingResponse(position, row.getStudentId(), row.getStudentName(),
                    row.getTotalActivities(), row.getTotalScore(), row.getTotalScore()
                            .divide(BigDecimal.valueOf(row.getTotalActivities()), 2, RoundingMode.HALF_UP),
                    row.getCorrectAnswers(), row.getTotalQuestions(), BigDecimal.valueOf(row.getCorrectAnswers())
                            .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(row.getTotalQuestions()), 2, RoundingMode.HALF_UP)));
            previous = row;
        }
        return result;
    }
}
