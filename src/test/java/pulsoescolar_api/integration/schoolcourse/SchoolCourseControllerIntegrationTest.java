package pulsoescolar_api.integration.schoolcourse;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import pulsoescolar_api.dto.classroom.ClassroomResponse;
import pulsoescolar_api.dto.schoolcourse.SchoolCourseResponse;
import pulsoescolar_api.integration.support.HttpIntegrationSupport;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.*;

class SchoolCourseControllerIntegrationTest extends HttpIntegrationSupport {
    private String path(ClassroomResponse room) {
        return "/api/classrooms/" + room.id() + "/school-courses";
    }

    private SchoolCourseResponse criarDisciplina(ClassroomResponse room) {
        var response = request(POST, path(room), teacherToken, Map.of("name", "Matemática"), SchoolCourseResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    @Test
    void criar_comProfessorVinculado_deveRetornar201EPersistirRelacionamentos() {
        var room = vincularProfessor(criarSala());
        var response = request(POST, path(room), teacherToken,
                Map.of("name", "  Matemática  "), SchoolCourseResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isPositive();
        assertThat(body.name()).isEqualTo("Matemática");
        assertThat(body.classroomId()).isEqualTo(room.id());
        assertThat(body.teacherId()).isEqualTo(teacher.getId());
        var saved = courses.findById(body.id()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Matemática");
        assertThat(saved.getClassroom().getId()).isEqualTo(room.id());
        assertThat(saved.getTeacher().getId()).isEqualTo(teacher.getId());
    }

    @Test
    void listar_comTurmaEDisciplinaCriadas_deveRetornar200SomenteComDisciplinasDaTurma() {
        var room = vincularProfessor(criarSala());
        var other = vincularProfessor(criarSala());
        var course = criarDisciplina(room);
        criarDisciplina(other);
        var response = request(GET, path(room), teacherToken, null, SchoolCourseResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(course);
    }

    @Test
    void criar_comNomeVazio_deveRetornar400ComErroSemPersistir() {
        var room = vincularProfessor(criarSala());
        long count = courses.count();
        var response = request(POST, path(room), teacherToken, Map.of("name", ""), ProblemDetail.class);

        assertProblem(response, HttpStatus.BAD_REQUEST, "inválidos");
        assertThat(response.getBody().getProperties().get("errors").toString()).contains("name");
        assertThat(courses.count()).isEqualTo(count);
    }

    @Test
    void criar_comDisciplinaDuplicada_deveRetornar409SemDuplicarRegistro() {
        var room = vincularProfessor(criarSala());
        criarDisciplina(room);
        long count = courses.count();
        var response = request(POST, path(room), teacherToken, Map.of("name", "Matemática"), ProblemDetail.class);

        assertProblem(response, HttpStatus.CONFLICT, "Já existe um cadastro");
        assertThat(courses.count()).isEqualTo(count);
    }

    @Test
    void listar_comTurmaInexistente_deveRetornar404ComMensagem() {
        var response = request(GET, "/api/classrooms/9223372036854775807/school-courses",
                teacherToken, null, ProblemDetail.class);

        assertProblem(response, HttpStatus.NOT_FOUND, "Sala não encontrada");
    }

    @Test
    void criar_comProfessorNaoVinculado_deveRetornar403SemPersistir() {
        var room = criarSala();
        long count = courses.count();
        var response = request(POST, path(room), teacherToken, Map.of("name", "Matemática"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(courses.count()).isEqualTo(count);
    }
}
