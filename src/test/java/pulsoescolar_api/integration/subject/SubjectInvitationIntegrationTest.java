package pulsoescolar_api.integration.subject;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pulsoescolar_api.dto.classroom.ClassroomResponse;
import pulsoescolar_api.dto.notification.NotificationResponse;
import pulsoescolar_api.dto.subject.*;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.subject.SubjectInvitationStatus;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.notification.UserNotificationRepository;
import pulsoescolar_api.repository.school.SchoolRepository;
import pulsoescolar_api.repository.subject.SubjectInvitationRepository;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.integration.support.HttpIntegrationSupport;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpMethod.*;

class SubjectInvitationIntegrationTest extends HttpIntegrationSupport {
    @Autowired UserRepository users;
    @Autowired SchoolRepository schools;
    @Autowired SubjectInvitationRepository invitations;
    @Autowired UserNotificationRepository notifications;
    @Autowired PlatformTransactionManager transactions;
    School school;
    SchoolUser recipient;
    SchoolUser outsider;
    ClassroomResponse room;
    SubjectResponse subject;
    String recipientToken;
    String outsiderToken;

    private TransactionTemplate tx() { return new TransactionTemplate(transactions); }

    private School school() {
        var school = new School();
        school.setNome("Escola " + UUID.randomUUID());
        school.setCnpj(String.format("%014d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100_000_000_000_000L)));
        school.setLogradouro("Rua"); school.setBairro("Centro"); school.setCidade("Cidade");
        return schools.saveAndFlush(school);
    }

    @BeforeEach
    void prepareInvitation() throws Exception {
        school = school();
        recipient = criarUsuario(Role.TEACHER);
        outsider = criarUsuario(Role.TEACHER);
        room = vincularProfessor(criarSala());
        tx().executeWithoutResult(status -> {
            for (var user : List.of(teacher, recipient, outsider)) users.findById(user.getId()).orElseThrow().setSchool(school);
            classrooms.findById(room.id()).orElseThrow().setSchool(school);
        });
        recipientToken = autenticar(recipient);
        outsiderToken = autenticar(outsider);
        var response = request(POST, "/api/classrooms/" + room.id() + "/subjects", teacherToken,
                Map.of("name", "Matemática", "syllabus", "", "topics", List.of(
                        Map.of("title", "Frações", "description", "Operações", "references", ""))), SubjectResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        subject = response.getBody();
        clearInvocations(emailSender);
    }

    private String sendPath() { return "/api/classrooms/" + room.id() + "/subjects/" + subject.id() + "/invitations"; }
    private String answerPath(Long id) { return "/api/subject-invitations/" + id; }

    private SubjectInvitationResponse invite() {
        var response = request(POST, sendPath(), teacherToken,
                Map.of("email", "  " + recipient.getEmail().toUpperCase(Locale.ROOT) + "  "), SubjectInvitationResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().status()).isEqualTo(SubjectInvitationStatus.PENDING);
        return response.getBody();
    }

    @Test
    void enviar_paraProfessorCadastrado_deveNotificarSemEmailNemAcessoAntecipado() {
        long accounts = users.count();
        var invitation = invite();
        assertThat(invitation.email()).isEqualTo(recipient.getEmail());
        assertThat(invitation.subjectId()).isEqualTo(subject.id());
        assertThat(invitation.classroomId()).isEqualTo(room.id());
        assertThat(invitation.subjectName()).isEqualTo(subject.name());
        assertThat(invitation.inviterName()).isEqualTo(teacher.getFullName());
        var mine = request(GET, "/api/subject-invitations", recipientToken, null, SubjectInvitationResponse[].class);
        assertThat(mine.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mine.getBody()).hasSize(1);
        assertThat(mine.getBody()[0].id()).isEqualTo(invitation.id());
        assertThat(mine.getBody()[0].email()).isEqualTo(invitation.email());
        assertThat(mine.getBody()[0].status()).isEqualTo(SubjectInvitationStatus.PENDING);
        assertThat(mine.getBody()[0].createdAt()).isNotNull();
        var inbox = request(GET, "/api/notifications", recipientToken, null, NotificationResponse[].class);
        assertThat(inbox.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(inbox.getBody()).hasSize(1);
        assertThat(inbox.getBody()[0].type()).isEqualTo("SUBJECT_INVITATION");
        assertThat(inbox.getBody()[0].title()).isNotBlank();
        assertThat(inbox.getBody()[0].message()).isNotBlank();
        assertThat(inbox.getBody()[0].read()).isFalse();
        assertThat(inbox.getBody()[0].readUrl()).startsWith("/api/notifications/").endsWith("/read");
        assertThat(request(GET, "/api/subject-invitations", outsiderToken, null, SubjectInvitationResponse[].class).getBody()).isEmpty();
        assertThat(request(GET, "/api/notifications", outsiderToken, null, NotificationResponse[].class).getBody()).isEmpty();
        assertThat(request(GET, "/api/classrooms/" + room.id() + "/subjects", recipientToken, null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(users.count()).isEqualTo(accounts);
        verifyNoInteractions(emailSender);
    }

    @Test
    void enviar_paraContaNaoCadastrada_deveRetornar404SemCriarContaOuNotificacao() {
        long accounts = users.count();
        long before = notifications.count();
        var response = request(POST, sendPath(), teacherToken, Map.of("email", UUID.randomUUID() + "@missing.test"), ProblemDetail.class);
        assertProblem(response, HttpStatus.NOT_FOUND, "Professor cadastrado não encontrado");
        assertThat(invitations.findByRecipientIdOrderByCreatedAtDescIdDesc(recipient.getId())).isEmpty();
        assertThat(users.count()).isEqualTo(accounts);
        assertThat(notifications.count()).isEqualTo(before);
        verifyNoInteractions(emailSender);
    }

    @Test
    void aceitar_comDestinatario_deveVincularProfessorATurmaEDisciplinaELiberarColaboracao() {
        var invitation = invite();
        var response = request(PATCH, answerPath(invitation.id()), recipientToken, Map.of("status", "ACCEPTED"), SubjectInvitationResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(SubjectInvitationStatus.ACCEPTED);
        assertThat(response.getBody().respondedAt()).isNotNull();
        var listing = request(GET, "/api/classrooms/" + room.id() + "/subjects", recipientToken, null, SubjectResponse[].class);
        assertThat(listing.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listing.getBody()).hasSize(1);
        assertThat(listing.getBody()[0].teacherIds()).containsExactlyInAnyOrder(teacher.getId(), recipient.getId());
        assertThat(classrooms.findByTeachersId(recipient.getId())).extracting(c -> c.getId()).contains(room.id());
        var theme = request(POST, "/api/classrooms/" + room.id() + "/subjects/" + subject.id() + "/themes",
                recipientToken, Map.of("name", "Geometria"), Map.class);
        assertThat(theme.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(theme.getBody()).containsEntry("name", "Geometria");
        assertThat(request(GET, "/api/notifications", recipientToken, null, NotificationResponse[].class).getBody()[0].read()).isTrue();
        assertThat(request(DELETE, "/api/classrooms/" + room.id() + "/teachers/" + recipient.getId(), managerToken, null, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verifyNoInteractions(emailSender);
    }

    @Test
    void rejeitar_deveMarcarConviteSemCriarVinculosEPermitirNovoConvite() {
        var invitation = invite();
        var response = request(PATCH, answerPath(invitation.id()), recipientToken, Map.of("status", "REJECTED"), SubjectInvitationResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(SubjectInvitationStatus.REJECTED);
        assertThat(classrooms.findByTeachersId(recipient.getId())).isEmpty();
        assertThat(request(GET, "/api/classrooms/" + room.id() + "/subjects", recipientToken, null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        var renewed = invite();
        assertThat(renewed.id()).isEqualTo(invitation.id());
        assertThat(renewed.respondedAt()).isNull();
        assertThat(notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(recipient.getId())).hasSize(2);
    }

    @Test
    void colaborador_aposAceite_devePoderConvidarOutroProfessorCadastrado() {
        var invitation = invite();
        assertThat(request(PATCH, answerPath(invitation.id()), recipientToken, Map.of("status", "ACCEPTED"), SubjectInvitationResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        var response = request(POST, sendPath(), recipientToken, Map.of("email", outsider.getEmail()), SubjectInvitationResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().email()).isEqualTo(outsider.getEmail());
        assertThat(response.getBody().inviterName()).isEqualTo(recipient.getFullName());
        assertThat(request(GET, "/api/subject-invitations", outsiderToken, null, SubjectInvitationResponse[].class).getBody())
                .extracting(SubjectInvitationResponse::id).containsExactly(response.getBody().id());
    }

    @Test
    void enviar_comConvitePendenteOuParticipanteExistente_deveRetornar409() {
        var invitation = invite();
        assertProblem(request(POST, sendPath(), teacherToken, Map.of("email", recipient.getEmail()), ProblemDetail.class),
                HttpStatus.CONFLICT, "pendente");
        assertProblem(request(POST, sendPath(), teacherToken, Map.of("email", teacher.getEmail()), ProblemDetail.class),
                HttpStatus.CONFLICT, "já participa");
        assertThat(notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(recipient.getId())).hasSize(1);
        assertThat(request(PATCH, answerPath(invitation.id()), recipientToken, Map.of("status", "ACCEPTED"), SubjectInvitationResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertProblem(request(POST, sendPath(), teacherToken, Map.of("email", recipient.getEmail()), ProblemDetail.class),
                HttpStatus.CONFLICT, "já participa");
    }

    @Test
    void responder_porOutroUsuarioOuDuasVezes_deveBloquear() {
        var invitation = invite();
        assertThat(request(PATCH, answerPath(invitation.id()), outsiderToken, Map.of("status", "ACCEPTED"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertProblem(request(PATCH, answerPath(invitation.id()), recipientToken, Map.of("status", "PENDING"), ProblemDetail.class),
                HttpStatus.BAD_REQUEST, "ACCEPTED ou REJECTED");
        assertThat(request(PATCH, answerPath(invitation.id()), recipientToken, Map.of("status", "REJECTED"), SubjectInvitationResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertProblem(request(PATCH, answerPath(invitation.id()), recipientToken, Map.of("status", "ACCEPTED"), ProblemDetail.class),
                HttpStatus.CONFLICT, "já foi respondido");
        assertThat(classrooms.findByTeachersId(recipient.getId())).isEmpty();
    }

    @Test
    void notificacao_devePermitirLeituraSomenteAoDestinatarioSemResponderConvite() {
        var invitation = invite();
        String readUrl = request(GET, "/api/notifications", recipientToken, null, NotificationResponse[].class).getBody()[0].readUrl();
        assertProblem(request(PATCH, readUrl, outsiderToken, null, ProblemDetail.class), HttpStatus.NOT_FOUND, "Notificação não encontrada");
        assertThat(request(PATCH, readUrl, recipientToken, null, Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        Instant firstRead = request(GET, "/api/notifications", recipientToken, null, NotificationResponse[].class).getBody()[0].readAt();
        assertThat(firstRead).isNotNull();
        assertThat(request(PATCH, readUrl, recipientToken, null, Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(request(GET, "/api/notifications", recipientToken, null, NotificationResponse[].class).getBody()[0].readAt()).isEqualTo(firstRead);
        assertThat(invitations.findById(invitation.id()).orElseThrow().getStatus()).isEqualTo(SubjectInvitationStatus.PENDING);
    }

    @Test
    void enviar_paraExcluidoOuPerfilIncorreto_deveBloquearSemNotificar() {
        tx().executeWithoutResult(status -> users.findById(recipient.getId()).orElseThrow().setDeletedAt(Instant.now()));
        assertProblem(request(POST, sendPath(), teacherToken, Map.of("email", recipient.getEmail()), ProblemDetail.class),
                HttpStatus.NOT_FOUND, "Professor cadastrado não encontrado");
        var student = criarUsuario(Role.STUDENT);
        tx().executeWithoutResult(status -> users.findById(student.getId()).orElseThrow().setSchool(school));
        assertThat(request(POST, sendPath(), teacherToken, Map.of("email", student.getEmail()), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(recipient.getId())).isEmpty();
    }

    @Test
    void enviar_semAutenticacaoOuSemParticipacao_deveRetornar401Ou403() {
        assertThat(request(POST, sendPath(), null, Map.of("email", recipient.getEmail()), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        tx().executeWithoutResult(status -> classrooms.findById(room.id()).orElseThrow().getTeachers()
                .add(users.findById(outsider.getId()).orElseThrow()));
        assertThat(request(POST, sendPath(), outsiderToken, Map.of("email", recipient.getEmail()), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(request(POST, sendPath(), managerToken, Map.of("email", recipient.getEmail()), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void enviar_ouAceitar_comOutraEscola_deveBloquearSemVinculo() {
        var another = school();
        tx().executeWithoutResult(status -> {
            var user = users.findById(recipient.getId()).orElseThrow();
            user.setSchool(another);
            user.getSchools().removeIf(value -> value.getId().equals(school.getId()));
        });
        assertThat(request(POST, sendPath(), teacherToken, Map.of("email", recipient.getEmail()), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        tx().executeWithoutResult(status -> users.findById(recipient.getId()).orElseThrow().setSchool(school));
        var invitation = invite();
        tx().executeWithoutResult(status -> users.findById(recipient.getId()).orElseThrow().setSchool(another));
        assertThat(request(PATCH, answerPath(invitation.id()), recipientToken, Map.of("status", "ACCEPTED"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(invitations.findById(invitation.id()).orElseThrow().getStatus()).isEqualTo(SubjectInvitationStatus.PENDING);
        assertThat(classrooms.findByTeachersId(recipient.getId())).isEmpty();
    }

    @Test
    void aceitar_aposRemocaoDoRemetenteDaTurma_deveBloquear() {
        var invitation = invite();
        tx().executeWithoutResult(status -> classrooms.findById(room.id()).orElseThrow().getTeachers().clear());
        assertThat(request(PATCH, answerPath(invitation.id()), recipientToken, Map.of("status", "ACCEPTED"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(classrooms.findByTeachersId(recipient.getId())).isEmpty();
    }

    @Test
    void enviar_comEmailInvalidoOuDisciplinaDeOutraTurma_deveRetornar400Ou404() {
        assertProblem(request(POST, sendPath(), teacherToken, Map.of("email", "invalido"), ProblemDetail.class),
                HttpStatus.BAD_REQUEST, "inválidos");
        var otherRoom = criarSala();
        assertProblem(request(POST, "/api/classrooms/" + otherRoom.id() + "/subjects/" + subject.id() + "/invitations",
                teacherToken, Map.of("email", recipient.getEmail()), ProblemDetail.class), HttpStatus.NOT_FOUND, "nesta turma");
    }

    @Test
    void enviar_simultaneamente_deveCriarSomenteUmConviteEUmaNotificacao() {
        var first = CompletableFuture.supplyAsync(() -> request(POST, sendPath(), teacherToken,
                Map.of("email", recipient.getEmail()), String.class));
        var second = CompletableFuture.supplyAsync(() -> request(POST, sendPath(), teacherToken,
                Map.of("email", recipient.getEmail()), String.class));
        assertThat(List.of(first.join().getStatusCode().value(), second.join().getStatusCode().value()))
                .containsExactlyInAnyOrder(201, 409);
        assertThat(invitations.findByRecipientIdOrderByCreatedAtDescIdDesc(recipient.getId())).hasSize(1);
        assertThat(notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(recipient.getId())).hasSize(1);
    }

    @Test
    void convite_deveUsarVinculoDoDestinatarioSemDependerDaSelecaoDoRemetente() {
        var another = school();
        tx().executeWithoutResult(status -> {
            users.findById(recipient.getId()).orElseThrow().getSchools().add(another);
            users.findById(teacher.getId()).orElseThrow().getSchools().add(another);
        });
        assertThat(request(PATCH, "/api/me/active-school", recipientToken,
                Map.of("schoolId", another.getId()), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        var invitation = invite();
        assertThat(request(PATCH, "/api/me/active-school", teacherToken,
                Map.of("schoolId", another.getId()), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(PATCH, "/api/me/active-school", recipientToken,
                Map.of("schoolId", school.getId()), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(PATCH, answerPath(invitation.id()), recipientToken,
                Map.of("status", "ACCEPTED"), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void responder_simultaneamente_deveAplicarSomenteUmaDecisao() {
        var invitation = invite();
        var first = CompletableFuture.supplyAsync(() -> request(PATCH, answerPath(invitation.id()), recipientToken,
                Map.of("status", "ACCEPTED"), String.class));
        var second = CompletableFuture.supplyAsync(() -> request(PATCH, answerPath(invitation.id()), recipientToken,
                Map.of("status", "ACCEPTED"), String.class));
        assertThat(List.of(first.join().getStatusCode().value(), second.join().getStatusCode().value()))
                .containsExactlyInAnyOrder(200, 409);
        assertThat(invitations.findById(invitation.id()).orElseThrow().getStatus()).isEqualTo(SubjectInvitationStatus.ACCEPTED);
    }
}
