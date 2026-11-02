package pulsoescolar_api.dto.activity;

import jakarta.validation.constraints.*;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

public record SubmitQuizRequest(@JsonDeserialize(contentUsing = QuizOptionDeserializer.class)
        @NotNull @Size(min = 10, max = 10) List<@NotNull @Min(0) @Max(3) Integer> answers) {}
