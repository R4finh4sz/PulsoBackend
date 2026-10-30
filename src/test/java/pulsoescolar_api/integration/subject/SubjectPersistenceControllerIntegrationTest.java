package pulsoescolar_api.integration.subject;

import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import pulsoescolar_api.dto.classroom.ClassroomResponse;
import pulsoescolar_api.dto.subject.SubjectResponse;
import pulsoescolar_api.integration.support.HttpIntegrationSupport;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.*;

class SubjectPersistenceControllerIntegrationTest extends HttpIntegrationSupport {
    private Map<String, Object> body(String name) {
        return Map.of("name", name, "syllabus", "", "topics",
                List.of(Map.of("title", "Tema", "description", "Conteudo", "references", "")));
    }

    private String path(ClassroomResponse room) {
        return "/api/classrooms/" + room.id() + "/subjects";
    }

    private SubjectResponse criarDisciplina(ClassroomResponse room) {
        var response = request(POST, path(room), teacherToken, body("Matemática"), SubjectResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    @Test
    void criar_comProfessorVinculado_deveRetornar201EPersistirRelacionamentos() {
        var room = vincularProfessor(criarSala());
        var response = request(POST, path(room), teacherToken,
                body("  Matemática  "), SubjectResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isPositive();
        assertThat(body.name()).isEqualTo("Matemática");
        assertThat(body.classroomId()).isEqualTo(room.id());
        assertThat(body.teacherId()).isEqualTo(teacher.getId());
        var saved = subjects.findById(body.id()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Matemática");
        assertThat(saved.getClassroom().getId()).isEqualTo(room.id());
        assertThat(saved.getTeacher().getId()).isEqualTo(teacher.getId());
    }

    @Test
    void listar_comTurmaEDisciplinaCriadas_deveRetornar200SomenteComDisciplinasDaTurma() {
        var room = vincularProfessor(criarSala());
        var other = vincularProfessor(criarSala());
        var subject = criarDisciplina(room);
        criarDisciplina(other);
        var response = request(GET, path(room), teacherToken, null, SubjectResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(subject);
    }

    @Test
    void criar_comNomeVazio_deveRetornar400ComErroSemPersistir() {
        var room = vincularProfessor(criarSala());
        long count = subjects.count();
        var response = request(POST, path(room), teacherToken, body(""), ProblemDetail.class);

        assertProblem(response, HttpStatus.BAD_REQUEST, "inválidos");
        assertThat(response.getBody().getProperties().get("errors").toString()).contains("name");
        assertThat(subjects.count()).isEqualTo(count);
    }

    @Test
    void criar_comDisciplinaDuplicada_deveRetornar409SemDuplicarRegistro() {
        var room = vincularProfessor(criarSala());
        criarDisciplina(room);
        long count = subjects.count();
        var response = request(POST, path(room), teacherToken, body("Matemática"), ProblemDetail.class);

        assertProblem(response, HttpStatus.CONFLICT, "Já existe um cadastro");
        assertThat(subjects.count()).isEqualTo(count);
    }

    @Test
    void listar_comTurmaInexistente_deveRetornar404ComMensagem() {
        var response = request(GET, "/api/classrooms/9223372036854775807/subjects",
                teacherToken, null, ProblemDetail.class);

        assertProblem(response, HttpStatus.NOT_FOUND, "Sala não encontrada");
    }

    @Test
    void criar_comProfessorNaoVinculado_deveRetornar403SemPersistir() {
        var room = criarSala();
        long count = subjects.count();
        var response = request(POST, path(room), teacherToken, body("Matemática"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(subjects.count()).isEqualTo(count);
    }
}
