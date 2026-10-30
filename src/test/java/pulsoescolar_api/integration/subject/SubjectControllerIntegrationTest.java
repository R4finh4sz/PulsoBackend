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
                Map.of("name", "Matemática", "syllabus", "", "topics", topics), SubjectResponse.class);
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
                Map.of("name", "Matemática", "syllabus", "", "topics", topics), ProblemDetail.class);

        assertProblem(response, HttpStatus.BAD_REQUEST, "inválidos");
        assertThat(subjects.findByClassroomId(room.id())).isEmpty();
    }

    @Test
    void criar_comTitulosDuplicadosIgnorandoCaixaEEspacos_deveRetornar400() {
        var room = vincularProfessor(criarSala());
        var response = request(POST, path(room), teacherToken, Map.of("name", "Matemática", "syllabus", "", "topics", List.of(
                Map.of("title", "Frações", "description", "A", "references", ""),
                Map.of("title", " frações ", "description", "B", "references", ""))), ProblemDetail.class);

        assertProblem(response, HttpStatus.BAD_REQUEST, "títulos dos tópicos");
        assertThat(subjects.findByClassroomId(room.id())).isEmpty();
    }

    @Test
    void criar_comCamposInvalidos_deveRetornar400ComErros() {
        var room = vincularProfessor(criarSala());
        for (Map<String, Object> body : List.of(
                Map.<String, Object>of("name", " ", "syllabus", "", "topics", payload().get("topics")),
                Map.<String, Object>of("name", "Matemática", "syllabus", "a".repeat(5001), "topics", payload().get("topics")),
                Map.<String, Object>of("name", "Matemática", "syllabus", "", "topics", List.of(Map.of("title", "T", "description", " ", "references", ""))),
                Map.<String, Object>of("name", "Matemática", "syllabus", "", "topics", List.of(Map.of("title", "T", "description", "a".repeat(2001), "references", ""))),
                Map.<String, Object>of("name", "Matemática", "syllabus", "", "topics", List.of(Map.of("title", "T", "description", "D", "references", "a".repeat(4001)))))) {
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
    void temas_comRegrasDoFrontend_deveValidarNormalizarEPersistir() {
        var room = vincularProfessor(criarSala());
        var subject = create(room);
        String url = path(room) + "/" + subject.id() + "/themes";
        for (String name : List.of("123", "!!!", "A".repeat(41))) {
            assertProblem(request(POST, url, teacherToken, Map.of("name", name), ProblemDetail.class),
                    HttpStatus.BAD_REQUEST, "inválidos");
        }
        String name = "Á".repeat(40);
        var creation = request(POST, url, teacherToken, Map.of("name", "  " + name + "  "), Map.class);
        assertThat(creation.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(creation.getBody()).containsEntry("name", name);
        var removal = request(DELETE, url + "?name=  " + name + "  ", teacherToken, null, Void.class);
        assertThat(removal.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(request(GET, url, teacherToken, null, String[].class).getBody()).isEmpty();
    }

    @Test
    void temas_comProfessorResponsavel_deveCriarListarERemover() {
        var room = vincularProfessor(criarSala());
        var subject = create(room);
        String url = path(room) + "/" + subject.id() + "/themes";
        var creation = request(POST, url, teacherToken, Map.of("name", " Frações "), Map.class);
        assertThat(creation.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(creation.getBody()).containsEntry("name", "Frações");
        var list = request(GET, url, teacherToken, null, String[].class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).containsExactly("Frações");
        var removal = request(DELETE, url + "?name=Frações", teacherToken, null, Void.class);
        assertThat(removal.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(request(GET, url, teacherToken, null, String[].class).getBody()).isEmpty();
    }

    @Test
    void temas_comNomeDuplicadoOuInexistente_deveRetornar409Ou404() {
        var room = vincularProfessor(criarSala());
        var subject = create(room);
        String url = path(room) + "/" + subject.id() + "/themes";
        assertThat(request(POST, url, teacherToken, Map.of("name", "Algebra"), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertProblem(request(POST, url, teacherToken, Map.of("name", " algebra "), ProblemDetail.class),
                HttpStatus.CONFLICT, "já existe");
        assertProblem(request(DELETE, url + "?name=ausente", teacherToken, null, ProblemDetail.class),
                HttpStatus.NOT_FOUND, "Tema não encontrado");
        assertThat(request(GET, url, teacherToken, null, String[].class).getBody()).containsExactly("Algebra");
    }

    @Test
    void temas_comNomeVazio_deveRetornar400SemAlterarTemas() {
        var room = vincularProfessor(criarSala());
        var subject = create(room);
        String url = path(room) + "/" + subject.id() + "/themes";
        assertProblem(request(POST, url, teacherToken, Map.of("name", " "), ProblemDetail.class),
                HttpStatus.BAD_REQUEST, "inválidos");
        assertThat(request(DELETE, url + "?name=", teacherToken, null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(GET, url, teacherToken, null, String[].class).getBody()).isEmpty();
    }

    @Test
    void temas_comDisciplinaDeOutraTurma_deveRetornar404() {
        var room = vincularProfessor(criarSala());
        var other = vincularProfessor(criarSala());
        var subject = create(room);
        assertProblem(request(GET, path(other) + "/" + subject.id() + "/themes", teacherToken, null, ProblemDetail.class),
                HttpStatus.NOT_FOUND, "nesta turma");
    }

    @Test
    void criar_semTokenOuSemVinculo_deveBloquear() {
        var room = criarSala();
        assertThat(request(POST, path(room), null, payload(), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(request(POST, path(room), teacherToken, payload(), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(subjects.findByClassroomId(room.id())).isEmpty();
    }

    @Test
    void temas_comProfessorVinculadoSemSerResponsavel_deveRetornar403() {
        var room = vincularProfessor(criarSala());
        var subject = create(room);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var owner = new SchoolUser();
            String id = UUID.randomUUID().toString();
            owner.setFullName("Outro Professor"); owner.setRa(id); owner.setEmail(id + "@integration.test");
            owner.setPasswordHash("unused"); owner.setRole(Role.TEACHER);
            users.saveAndFlush(owner);
            classrooms.findById(room.id()).orElseThrow().getTeachers().add(owner);
            subjects.findById(subject.id()).orElseThrow().setTeacher(owner);
        });
        var response = request(POST, path(room) + "/" + subject.id() + "/themes", teacherToken, Map.of("name", "Tema"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listar_comProfessorDeOutraEscolaMesmoVinculado_deveRetornar403() {
        var room = vincularProfessor(criarSala());
        var school = new School();
        school.setNome("Escola");
        school.setCnpj(String.format("%014d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100_000_000_000_000L)));
        school.setLogradouro("Rua"); school.setBairro("Centro"); school.setCidade("Cidade");
        schools.saveAndFlush(school);
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                users.findById(teacher.getId()).orElseThrow().setSchool(school));
        assertThat(request(GET, path(room), teacherToken, null, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
