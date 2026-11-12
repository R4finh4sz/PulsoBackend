package pulsoescolar_api.dto.activity;

import java.util.List;

public record QuizResponse(ActivityResponse activity, List<Question> questions) {
    public record Question(Long id, String question, List<String> options, String theme) {}
}
