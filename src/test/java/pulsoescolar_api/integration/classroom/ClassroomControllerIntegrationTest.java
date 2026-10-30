package pulsoescolar_api.integration.classroom;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import pulsoescolar_api.dto.classroom.ClassroomResponse;
import pulsoescolar_api.dto.user.UserResponse;
import pulsoescolar_api.integration.support.HttpIntegrationSupport;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.*;

class ClassroomControllerIntegrationTest extends HttpIntegrationSupport {
    private static final String PATH = "/api/classrooms";

    @Test
    void criar_comAdministrador_deveRetornar201ComCorpoHeaderEPersistencia() {
        String name = "Turma " + UUID.randomUUID();
        var response = request(POST, PATH, managerToken,
                Map.of("name", name, "identifier", "A"), ClassroomResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getContentType()).isNotNull()
                .matches(type -> type.isCompatibleWith(MediaType.APPLICATION_JSON));
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isPositive();
        assertThat(body.name()).isEqualTo(name);
        assertThat(body.identifier()).isEqualTo("A");
        assertThat(body.teacherIds()).isEmpty();
        var saved = classrooms.findById(body.id()).orElseThrow();
        assertThat(saved.getName()).isEqualTo(name);
        assertThat(saved.getIdentifier()).isEqualTo("A");
    }

    @Test
    void listar_comSalaCriada_deveRetornar200ComSalaNaLista() {
        var room = criarSala();
        var response = request(GET, PATH, managerToken, null, ClassroomResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(room);
    }

    @Test
    void buscar_comIdExistente_deveRetornar200ComDadosDaSala() {
        var room = criarSala();
        var response = request(GET, PATH + "/" + room.id(), managerToken, null, ClassroomResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(room);
    }

    @Test
    void atualizar_comNomeValido_deveRetornar200EPersistirSemAlterarIdentificador() {
        var room = criarSala();
        String name = "Atualizada " + UUID.randomUUID();
        var response = request(PATCH, PATH + "/" + room.id(), managerToken,
                Map.of("name", name), ClassroomResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new ClassroomResponse(room.id(), name, "A", room.teacherIds()));
        var saved = classrooms.findById(room.id()).orElseThrow();
        assertThat(saved.getName()).isEqualTo(name);
        assertThat(saved.getIdentifier()).isEqualTo("A");
    }

    @Test
    void professores_comVinculo_deveRetornar200ComProfessorDaSala() {
        var room = vincularProfessor(criarSala());
        var response = request(GET, PATH + "/" + room.id() + "/teachers", managerToken, null, UserResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].id()).isEqualTo(teacher.getId());
        assertThat(response.getBody()[0].email()).isEqualTo(teacher.getEmail());
    }

    @Test
    void criar_comNomeVazio_deveRetornar400ComErrosDeValidacao() {
        var response = request(POST, PATH, managerToken,
                Map.of("name", "", "identifier", "A"), ProblemDetail.class);

        assertProblem(response, HttpStatus.BAD_REQUEST, "inválidos");
        assertThat(response.getBody().getProperties().get("errors").toString()).contains("name");
    }

    @Test
    void buscar_comIdInexistente_deveRetornar404ComMensagem() {
        var response = request(GET, PATH + "/9223372036854775807", managerToken, null, ProblemDetail.class);

        assertProblem(response, HttpStatus.NOT_FOUND, "Sala não encontrada");
    }

    @Test
    void criar_comSalaDuplicada_deveRetornar409SemDuplicarRegistro() {
        var room = criarSala();
        long count = classrooms.count();
        var response = request(POST, PATH, managerToken,
                Map.of("name", room.name(), "identifier", room.identifier()), ProblemDetail.class);

        assertProblem(response, HttpStatus.CONFLICT, "Já existe um cadastro");
        assertThat(classrooms.count()).isEqualTo(count);
    }

    @Test
    void criar_semToken_deveRetornar401ComDesafioBearer() {
        var response = request(POST, PATH, null,
                Map.of("name", "Bloqueada", "identifier", "A"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
    }

    @Test
    void criar_comProfessor_deveRetornar403SemPersistir() {
        long count = classrooms.count();
        var response = request(POST, PATH, teacherToken,
                Map.of("name", "Bloqueada", "identifier", "A"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(classrooms.count()).isEqualTo(count);
    }
}
