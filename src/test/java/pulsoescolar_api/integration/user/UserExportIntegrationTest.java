package pulsoescolar_api.integration.user;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import pulsoescolar_api.dto.user.UserExportResponse;
import pulsoescolar_api.entity.notification.UserNotification;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.subject.Subject;
import pulsoescolar_api.entity.subject.SubjectInvitation;
import pulsoescolar_api.entity.subject.SubjectInvitationStatus;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.integration.support.HttpIntegrationSupport;
import pulsoescolar_api.repository.notification.UserNotificationRepository;
import pulsoescolar_api.repository.school.SchoolRepository;
import pulsoescolar_api.repository.subject.SubjectInvitationRepository;
import pulsoescolar_api.repository.user.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;

class UserExportIntegrationTest extends HttpIntegrationSupport {
    private static final String PATH = "/api/me/export";
    @Autowired UserRepository users;
    @Autowired SchoolRepository schools;
    @Autowired RegistrationRepository registrations;
    @Autowired AccountDeletionRepository deletions;
    @Autowired UserNotificationRepository notifications;
    @Autowired SubjectInvitationRepository invitations;
    @Autowired java.time.Clock clock;

    UserNotification notification(SchoolUser recipient, String message) {
        var notification = new UserNotification();
        notification.setRecipient(recipient);
        notification.setType("TEST");
        notification.setTitle("Export");
        notification.setMessage(message);
        notification.setCreatedAt(Instant.now());
        return notifications.saveAndFlush(notification);
    }

    void deletion(SchoolUser requester, String reason) {
        var deletion = new AccountDeletionRequest();
        deletion.setRequester(requester);
        deletion.setReason(reason);
        deletion.setStatus(DeletionStatus.PENDING);
        deletion.setRequestedAt(Instant.now());
        deletions.saveAndFlush(deletion);
    }

    @Test
    void exportIncludesOwnDataAndExcludesCredentialsAndOtherUsers() {
        var school = new School();
        school.setNome("Escola export");
        school.setCnpj(UUID.randomUUID().toString().substring(0, 18));
        school.setLogradouro("Rua");
        school.setBairro("Centro");
        school.setCidade("Cidade");
        schools.saveAndFlush(school);
        teacher = users.findById(teacher.getId()).orElseThrow();
        teacher.setSchools(new java.util.HashSet<>());
        teacher.setSchool(school);
        teacher.setBirthDate(LocalDate.of(1990, 1, 1));
        teacher.setProfilePhoto(new byte[] {1, 2, 3});
        teacher.setTermsAccepted(true);
        teacher.setAcceptedTermVersions(new java.util.HashSet<>(java.util.Set.of(1L)));
        users.saveAndFlush(teacher);
        var registration = new RegistrationRequest();
        registration.setUser(teacher);
        registration.setFullName(teacher.getFullName());
        registration.setRa(teacher.getRa());
        registration.setEmail(teacher.getEmail());
        registration.setRole(Role.TEACHER);
        registration.setSchool(school);
        registration.setTermsVersion(1L);
        registration.setStatus(RegistrationStatus.APPROVED);
        registration.setRequestedAt(Instant.now());
        registration.setPasswordHash("registration-secret");
        registrations.saveAndFlush(registration);
        var room = vincularProfessor(criarSala());
        var schoolRoom = classrooms.findById(room.id()).orElseThrow();
        schoolRoom.setSchool(school);
        classrooms.saveAndFlush(schoolRoom);
        criarSala();
        var outsider = criarUsuario(Role.TEACHER);
        var outsiderNotification = notification(outsider, "other-private-notification");
        deletion(outsider, "other-private-deletion");
        deletion(teacher, "Meu pedido");
        var notification = notification(teacher, "Minha mensagem");
        var subject = new Subject();
        subject.setName("Disciplina export");
        subject.setClassroom(classrooms.findById(room.id()).orElseThrow());
        subject.setTeacher(outsider);
        subjects.saveAndFlush(subject);
        var invitation = new SubjectInvitation();
        invitation.setSubject(subject);
        invitation.setRecipient(teacher);
        invitation.setInviter(outsider);
        invitation.setNotification(notification);
        invitation.setStatus(SubjectInvitationStatus.PENDING);
        invitation.setCreatedAt(Instant.now());
        invitations.saveAndFlush(invitation);
        var outsiderInvitation = new SubjectInvitation();
        outsiderInvitation.setSubject(subject);
        outsiderInvitation.setRecipient(outsider);
        outsiderInvitation.setInviter(teacher);
        outsiderInvitation.setNotification(outsiderNotification);
        outsiderInvitation.setStatus(SubjectInvitationStatus.PENDING);
        outsiderInvitation.setCreatedAt(Instant.now());
        invitations.saveAndFlush(outsiderInvitation);

        var beforeExport = clock.instant();
        var response = request(GET, PATH + "?userId=" + outsider.getId(), teacherToken, null, UserExportResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("pulso-dados.json");
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.exportedAt()).isBetween(beforeExport, clock.instant());
        assertThat(body.user().id()).isEqualTo(teacher.getId());
        assertThat(body.user().fullName()).isEqualTo(teacher.getFullName());
        assertThat(body.user().email()).isEqualTo(teacher.getEmail());
        assertThat(body.user().ra()).isEqualTo(teacher.getRa());
        assertThat(body.user().role()).isEqualTo(Role.TEACHER);
        assertThat(body.birthDate()).isEqualTo(teacher.getBirthDate());
        assertThat(body.profilePhoto()).containsExactly(teacher.getProfilePhoto());
        assertThat(body.termsAccepted()).isTrue();
        assertThat(body.termsAcceptedVersions()).containsExactly("1.0");
        assertThat(body.schools()).hasSize(1);
        assertThat(body.schools().getFirst().id()).isEqualTo(school.getId().toString());
        assertThat(body.classrooms()).extracting(UserExportResponse.Classroom::id).containsExactly(room.id());
        assertThat(body.registration().id()).isEqualTo(registration.getId());
        assertThat(body.deletionRequests()).extracting(UserExportResponse.Deletion::reason).containsExactly("Meu pedido");
        assertThat(body.notifications()).hasSize(1);
        assertThat(body.notifications().getFirst().id()).isEqualTo(notification.getId());
        assertThat(body.receivedInvitations()).extracting(UserExportResponse.Invitation::id).containsExactly(invitation.getId());
        var raw = request(GET, PATH, teacherToken, null, String.class).getBody();
        assertThat(raw).doesNotContain("password", "Hash", "token", "jwt", "refresh", "secret", "code", "teacherIds", "reviewedBy",
                "inviter", "recipient", outsider.getEmail(), "registration-secret", teacher.getPasswordHash(),
                "other-private-notification", "other-private-deletion");
    }

    @Test
    void emptyAccountCanExportForEveryRole() throws Exception {
        for (var role : Role.values()) {
            var account = criarUsuario(role);
            var token = autenticar(account);
            var response = request(GET, PATH, token, null, UserExportResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            var body = response.getBody();
            assertThat(body.user().id()).isEqualTo(account.getId());
            assertThat(body.schools()).isEmpty();
            assertThat(body.classrooms()).isEmpty();
            assertThat(body.registration()).isNull();
            assertThat(body.deletionRequests()).isEmpty();
            assertThat(body.notifications()).isEmpty();
            assertThat(body.receivedInvitations()).isEmpty();
        }
    }

    @Test
    void anonymousExportIsUnauthorized() {
        assertThat(request(GET, PATH, null, null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void pendingTwoFactorCannotExport() {
        var account = criarUsuario(Role.STUDENT);
        var login = request(org.springframework.http.HttpMethod.POST, "/api/auth/login", null,
                java.util.Map.of("email", account.getEmail(), "password", "SenhaTeste123"),
                pulsoescolar_api.dto.auth.LoginResponse.class);
        assertThat(request(GET, PATH, login.getBody().accessToken(), null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
