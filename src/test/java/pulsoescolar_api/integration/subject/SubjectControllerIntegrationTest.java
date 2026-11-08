package pulsoescolar_api.integration.subject;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pulsoescolar_api.dto.classroom.ClassroomResponse;
import pulsoescolar_api.dto.subject.*;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.school.SchoolRepository;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.integration.support.HttpIntegrationSupport;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.*;

class SubjectControllerIntegrationTest extends HttpIntegrationSupport {
    @Autowired UserRepository users;
    @Autowired SchoolRepository schools;
    @Autowired PlatformTransactionManager transactions;

    private String path(ClassroomResponse room) {
        return "/api/classrooms/" + room.id() + "/subjects";
    }

    private Map<String, Object> payload() {
        return Map.of("name", "Matemática", "syllabus", "Conteúdos e objetivos",
                "topics", List.of(Map.of("title", "Frações", "description", "Operações", "references", "Livro"),
                        Map.of("title", "Geometria", "description", "Figuras", "references", "")));
    }

    private SubjectResponse create(ClassroomResponse room) {
        var response = request(POST, path(room), teacherToken, payload(), SubjectResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    @Test
    void editarEmenta_deveSubstituirTopicosEPersistir() {
        var room = vincularProfessor(criarSala());
        var subject = create(room);
        String url = path(room) + "/" + subject.id() + "/syllabus";
        var topic = Map.of("title", " Álgebra ", "description", " Equações ", "references", " Livro novo ");
        var response = request(PATCH, url, teacherToken, Map.of("topics", List.of(topic)), SubjectResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo(subject.name());
        assertThat(response.getBody().syllabus()).isEqualTo("Equações");
        assertThat(response.getBody().topics()).containsExactly(
                new CreateSubjectRequest.Topic("Álgebra", "Equações", "Livro novo"));
        assertThat(request(GET, path(room), teacherToken, null, SubjectResponse[].class).getBody())
                .containsExactly(response.getBody());
        var expanded = List.of(new CreateSubjectRequest.Topic("Álgebra", "Equações", "Livro novo"),
                new CreateSubjectRequest.Topic("Geometria", "Áreas", ""),
                new CreateSubjectRequest.Topic("Estatística", "Médias", "Artigo"));
        var added = request(PATCH, url, teacherToken, Map.of("topics", expanded), SubjectResponse.class);
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(added.getBody().topics()).containsExactlyElementsOf(expanded);
        assertThat(request(GET, path(room), teacherToken, null, SubjectResponse[].class).getBody())
                .containsExactly(added.getBody());
    }

    @Test
    void editarEmenta_deveValidarConteudoEVinculoSemAlterarTopicos() throws Exception {
        var room = vincularProfessor(criarSala());
        var subject = create(room);
        String url = path(room) + "/" + subject.id() + "/syllabus";
        for (Object body : List.of(Map.of(), Map.of("topics", List.of()),
                Map.of("topics", List.of(Map.of("title", "Tema", "description", " ", "references", ""))),
                Map.of("topics", List.of(
                        Map.of("title", "Tema", "description", "A", "references", ""),
                        Map.of("title", " tema ", "description", "B", "references", ""))))) {
            assertThat(request(PATCH, url, teacherToken, body, String.class).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
        var body = Map.of("topics", payload().get("topics"));
        assertThat(request(PATCH, url, null, body, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var outsider = criarUsuario(Role.TEACHER);
        request(PUT, "/api/classrooms/" + room.id() + "/teachers/" + outsider.getId(), managerToken, null, String.class);
        assertThat(request(PATCH, url, autenticar(outsider), body, String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        var otherRoom = vincularProfessor(criarSala());
        assertThat(request(PATCH, path(otherRoom) + "/" + subject.id() + "/syllabus", teacherToken, body,
                String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(GET, path(room), teacherToken, null, SubjectResponse[].class).getBody()).containsExactly(subject);
    }

    @Test
    void criar_comConteudo_devePersistirEListarEmentaConcatenada() {
        var room = vincularProfessor(criarSala());
        var subject = create(room);

        assertThat(subject.name()).isEqualTo("Matemática");
        assertThat(subject.syllabus()).isEqualTo("Operações\n\nFiguras");
        assertThat(subject.teacherIds()).containsExactly(teacher.getId());
        assertThat(subject.topics()).containsExactly(new CreateSubjectRequest.Topic("Frações", "Operações", "Livro"),
                new CreateSubjectRequest.Topic("Geometria", "Figuras", ""));
        var response = request(GET, path(room), teacherToken, null, SubjectResponse[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(subject);
        assertThat(subjects.findById(subject.id()).orElseThrow().getSyllabus()).isEqualTo("Conteúdos e objetivos");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 15})
    void criar_comQuantidadeValidaDeTopicos_devePreservarOrdem(int count) {
        var room = vincularProfessor(criarSala());
        var topics = java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> Map.of("title", "Tópico " + i, "description", "Descrição", "references", "")).toList();
        var response = request(POST, path(room), teacherToken,
                Map.of("name", "Matemática", "syllabus", "Conteúdos e objetivos", "topics", topics), SubjectResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().syllabus()).isEqualTo(String.join("\n\n", java.util.Collections.nCopies(count, "Descrição")));
        var list = request(GET, path(room), teacherToken, null, SubjectResponse[].class);
        assertThat(list.getBody()).hasSize(1);
        assertThat(list.getBody()[0].topics()).extracting(CreateSubjectRequest.Topic::title)
                .containsExactlyElementsOf(topics.stream().map(t -> t.get("title")).toList());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 16})
    void criar_comQuantidadeInvalidaDeTopicos_deveRetornar400(int count) {
        var room = vincularProfessor(criarSala());
        var topics = java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> Map.of("title", "Tópico " + i, "description", "Descrição", "references", "")).toList();
        var response = request(POST, path(room), teacherToken,
                Map.of("name", "Matemática", "syllabus", "Conteúdos e objetivos", "topics", topics), ProblemDetail.class);

        assertProblem(response, HttpStatus.BAD_REQUEST, "inválidos");
        assertThat(subjects.findByClassroomId(room.id())).isEmpty();
    }

    @Test
    void criar_comTitulosDuplicadosIgnorandoCaixaEEspacos_deveRetornar400() {
        var room = vincularProfessor(criarSala());
        var response = request(POST, path(room), teacherToken, Map.of("name", "Matemática", "syllabus", "Conteúdos e objetivos", "topics", List.of(
                Map.of("title", "Frações", "description", "A", "references", ""),
                Map.of("title", " frações ", "description", "B", "references", ""))), ProblemDetail.class);

        assertProblem(response, HttpStatus.BAD_REQUEST, "títulos dos tópicos");
        assertThat(subjects.findByClassroomId(room.id())).isEmpty();
    }

    @Test
    void criar_comCamposInvalidos_deveRetornar400ComErros() {
        var room = vincularProfessor(criarSala());
        for (Map<String, Object> body : List.of(
                Map.<String, Object>of("name", " ", "syllabus", "Conteúdos e objetivos", "topics", payload().get("topics")),
                Map.<String, Object>of("name", "Matemática", "syllabus", "a".repeat(5001), "topics", payload().get("topics")),
                Map.<String, Object>of("name", "Matemática", "syllabus", "Conteúdos e objetivos", "topics", List.of(Map.of("title", "T", "description", " ", "references", ""))),
                Map.<String, Object>of("name", "Matemática", "syllabus", "Conteúdos e objetivos", "topics", List.of(Map.of("title", "T", "description", "a".repeat(2001), "references", ""))),
                Map.<String, Object>of("name", "Matemática", "syllabus", "Conteúdos e objetivos", "topics", List.of(Map.of("title", "T", "description", "D", "references", "a".repeat(4001)))))) {
            assertProblem(request(POST, path(room), teacherToken, body, ProblemDetail.class),
                    HttpStatus.BAD_REQUEST, "inválidos");
        }
    }

    @Test
    void criar_comTextosNosLimitesEEspacos_deveValidarDepoisDeNormalizar() {
        var room = vincularProfessor(criarSala());
        var response = request(POST, path(room), teacherToken, Map.of(
                "name", "  " + "A".repeat(100) + "  ", "syllabus", "  " + "E".repeat(5000) + "  ",
                "topics", List.of(Map.of("title", "  " + "Á".repeat(40) + "  ",
                        "description", "  " + "D".repeat(2000) + "  ",
                        "references", "  " + "R".repeat(4000) + "  "))), SubjectResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().name()).hasSize(100);
        assertThat(response.getBody().syllabus()).isEqualTo("D".repeat(2000));
        assertThat(response.getBody().topics()).containsExactly(new CreateSubjectRequest.Topic(
                "Á".repeat(40), "D".repeat(2000), "R".repeat(4000)));
    }

    @Test
    void criar_comEmentaVaziaOuSemTopicos_deveRetornar400() {
        var room = vincularProfessor(criarSala());
        for (String syllabus : List.of("", " ", "\n\t")) {
            var body = new java.util.LinkedHashMap<String, Object>(payload());
            body.put("syllabus", syllabus);
            assertThat(request(POST, path(room), teacherToken, body, ProblemDetail.class).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
        var body = new java.util.LinkedHashMap<String, Object>(payload());
        body.put("topics", List.of());
        assertThat(request(POST, path(room), teacherToken, body, ProblemDetail.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void criar_semEmentaOuReferencias_deveRetornar400ComCampoCorreto() {
        var room = vincularProfessor(criarSala());
        for (String field : List.of("syllabus", "references")) {
            for (boolean explicitNull : List.of(false, true)) {
                var body = new java.util.LinkedHashMap<String, Object>(payload());
                var topic = new java.util.LinkedHashMap<String, Object>(Map.of(
                        "title", "Frações", "description", "Operações", "references", ""));
                var target = field.equals("syllabus") ? body : topic;
                if (explicitNull) target.put(field, null); else target.remove(field);
                body.put("topics", List.of(topic));
                var response = request(POST, path(room), teacherToken, body, ProblemDetail.class);
                assertProblem(response, HttpStatus.BAD_REQUEST, "inválidos");
                assertThat(response.getBody().getProperties().get("errors").toString()).contains(field);
            }
        }
        assertThat(subjects.findByClassroomId(room.id())).isEmpty();
    }

    @Test
    void criar_comTipoDiferenteDeString_deveRetornar400() {
        var room = vincularProfessor(criarSala());
        for (String field : List.of("syllabus", "references")) {
            for (Object value : List.of(123, true, List.of("texto"), Map.of("text", "texto"))) {
                var body = new java.util.LinkedHashMap<String, Object>(payload());
                var topic = new java.util.LinkedHashMap<String, Object>(Map.of(
                        "title", "Frações", "description", "Operações", "references", ""));
                if (field.equals("syllabus")) body.put(field, value); else topic.put(field, value);
                body.put("topics", List.of(topic));
                assertThat(request(POST, path(room), teacherToken, body, String.class).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST);
            }
        }
        assertThat(subjects.findByClassroomId(room.id())).isEmpty();
    }

    @Test
    void rotasDeTemasAvulsos_naoDevemExistir() {
        var room = vincularProfessor(criarSala());
        var subject = create(room);
        String url = path(room) + "/" + subject.id() + "/themes";
        for (var method : List.of(GET, POST, DELETE)) {
            assertThat(request(method, url, teacherToken, Map.of("name", "Tema"), String.class).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void criar_semTokenOuSemVinculo_deveBloquear() {
        var room = criarSala();
        assertThat(request(POST, path(room), null, payload(), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(request(POST, path(room), teacherToken, payload(), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(subjects.findByClassroomId(room.id())).isEmpty();
    }

    @Test
    void listar_comProfessorDeOutraEscolaMesmoVinculado_deveRetornar403() {
        var room = vincularProfessor(criarSala());
        var school = new School();
        school.setNome("Escola");
        school.setLogradouro("Rua"); school.setBairro("Centro"); school.setCidade("Cidade");
        schools.saveAndFlush(school);
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                users.findById(teacher.getId()).orElseThrow().setSchool(school));
        assertThat(request(GET, path(room), teacherToken, null, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
