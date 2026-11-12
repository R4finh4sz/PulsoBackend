package pulsoescolar_api.dto.classroom;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import pulsoescolar_api.entity.classroom.DeliberationAction;
import pulsoescolar_api.entity.classroom.DeliberationResult;

public record DeliberateClassroomRequest(
        @NotNull(message = "Informe a turma.") Long classroomId,
        @NotNull(message = "Informe a ação: TRANSFER ou GRADUATE.") DeliberationAction action,
        @NotEmpty(message = "Informe o resultado de todos os alunos.")
        List<@NotNull @Valid StudentResult> students) {
    public record StudentResult(
            @NotNull(message = "Informe o aluno.") Long studentId,
            @NotNull(message = "Informe o resultado: APPROVED ou FAILED.") DeliberationResult result,
            Long destinationClassroomId) {}
}
