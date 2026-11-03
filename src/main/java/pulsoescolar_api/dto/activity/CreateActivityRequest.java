package pulsoescolar_api.dto.activity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.time.Instant;
import tools.jackson.databind.annotation.JsonDeserialize;
import pulsoescolar_api.dto.subject.SubjectStringDeserializer;

public record CreateActivityRequest(@NotNull(message = "Informe a data de encerramento do quiz.") Instant closesAt,
        @NotNull @Size(min = 10, max = 10, message = "O quiz deve conter exatamente 10 questões.")
        List<@NotNull @Valid Question> questions) {
    public record Question(@JsonDeserialize(using = SubjectStringDeserializer.class)
            @NotBlank(message = "Informe a pergunta.") @Size(max = 1000) String question,
            @JsonDeserialize(contentUsing = SubjectStringDeserializer.class)
            @NotNull @Size(min = 4, max = 4) List<@NotBlank(message = "Preencha as quatro alternativas.") @Size(max = 500) String> options,
            @JsonDeserialize(using = SubjectStringDeserializer.class)
            @NotBlank(message = "Selecione um tema.") String theme,
            @JsonDeserialize(using = QuizOptionDeserializer.class) @NotNull @Min(0) @Max(3) Integer correctOption) {
        public Question {
            question = trim(question);
            theme = trim(theme);
            if (options != null) options = options.stream().map(CreateActivityRequest::trim).toList();
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.replaceAll("^[\\s\\p{Z}\\uFEFF]+|[\\s\\p{Z}\\uFEFF]+$", "");
    }
}
