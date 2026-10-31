package pulsoescolar_api.integration.classroom;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import pulsoescolar_api.dto.classroom.ClassroomResponse;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.integration.support.HttpIntegrationSupport;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;

class TeacherClassroomIntegrationTest extends HttpIntegrationSupport {
    private static final String PATH = "/api/teacher/classrooms";

    @Test
    void listar_deveRetornarSomenteTurmasDoProfessorAutenticado() throws Exception {
        var room = vincularProfessor(criarSala());
        criarSala();
        var otherTeacherToken = autenticar(criarUsuario(Role.TEACHER));

        var response = request(GET, PATH, teacherToken, null, ClassroomResponse[].class);
        var otherResponse = request(GET, PATH, otherTeacherToken, null, ClassroomResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(room);
        assertThat(otherResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(otherResponse.getBody()).isEmpty();
    }

    @Test
    void listar_semToken_deveRetornar401() {
        assertThat(request(GET, PATH, null, null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listar_comOutroPerfil_deveRetornar403() throws Exception {
        for (var role : new Role[] {Role.ADMIN, Role.PEDAGOGICAL_COORDINATOR, Role.STUDENT}) {
            var token = autenticar(criarUsuario(role));
            assertThat(request(GET, PATH, token, null, String.class).getStatusCode())
                    .as("Perfil %s", role).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
