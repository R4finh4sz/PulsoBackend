package pulsoescolar_api.integration.user;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pulsoescolar_api.dto.user.*;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.integration.support.HttpIntegrationSupport;
import pulsoescolar_api.repository.school.SchoolRepository;
import pulsoescolar_api.repository.user.*;
import pulsoescolar_api.repository.notification.UserNotificationRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SchoolInvitationIntegrationTest extends HttpIntegrationSupport {
    private static final String PATH = "/api/me/school-invitations";
    @Autowired SchoolRepository schools;
    @Autowired UserRepository users;
    @Autowired SchoolInvitationRepository invitations;
    @Autowired RegistrationRepository registrations;
    @Autowired UserNotificationRepository notifications;
    @Autowired PlatformTransactionManager transactions;
    String coordinatorToken;
    SchoolUser coordinator;
    School target;
    School original;

    TransactionTemplate tx() { return new TransactionTemplate(transactions); }

    School school() {
        var school = new School(); school.setNome("Escola " + UUID.randomUUID()); school.setLogradouro("Rua");
        school.setBairro("Centro"); school.setCidade("Cidade");
        return schools.saveAndFlush(school);
    }

    @BeforeEach
    void prepareSchool() throws Exception {
        target = school(); original = school();
        coordinator = criarUsuario(Role.PEDAGOGICAL_COORDINATOR);
        tx().executeWithoutResult(status -> {
            users.findById(coordinator.getId()).orElseThrow().setSchool(target);
            users.findById(teacher.getId()).orElseThrow().setSchool(original);
        });
        coordinatorToken = autenticar(coordinator);
    }

    InvitationReceipt invite() {
        var response = request(POST, "/api/invitations/teachers", coordinatorToken,
                Map.of("email", teacher.getEmail()), InvitationReceipt.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().internalInvitationId()).isPositive();
        assertThat(response.getBody().token()).isNull();
        return response.getBody();
    }

    boolean linked(Long schoolId) {
        return tx().execute(status -> users.findById(teacher.getId()).orElseThrow().belongsToSchool(schoolId));
    }

    @Test
    void refusesPendingInvitationToDeactivatedSchool() {
        var invitation = invite();
        tx().executeWithoutResult(status -> schools.findById(target.getId()).orElseThrow().setActive(false));
        assertThat(request(PATCH, PATH + "/" + invitation.internalInvitationId(), teacherToken,
                Map.of("status", "ACCEPTED"), String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(linked(target.getId())).isFalse();
        assertThat(invitations.findById(invitation.internalInvitationId()).orElseThrow().getStatus())
                .isEqualTo(SchoolInvitationStatus.PENDING);
    }

    @Test
    void acceptsAnotherSchoolWhenOriginalWasDeactivated() {
        var invitation = invite();
        tx().executeWithoutResult(status -> schools.findById(original.getId()).orElseThrow().setActive(false));
        assertThat(request(PATCH, PATH + "/" + invitation.internalInvitationId(), teacherToken,
                Map.of("status", "ACCEPTED"), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        var response = request(GET, "/api/me", teacherToken, null, UserResponse.class).getBody();
        assertThat(response.schoolId()).isEqualTo(target.getId());
        assertThat(response.schoolUnavailable()).isFalse();
    }

    @Test
    void acceptLinksExistingAccountWithoutRegistrationMailOrChangingActiveSchool() {
        long userCount = users.count(); long registrationCount = registrations.count();
        clearInvocations(emailSender);
        var invitation = invite();
        verify(emailSender, never()).send(eq(teacher.getEmail()), anyString(), anyString());
        assertThat(linked(target.getId())).isFalse();
        var list = request(GET, PATH, teacherToken, null, SchoolInvitationResponse[].class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).extracting(SchoolInvitationResponse::id).containsExactly(invitation.internalInvitationId());
        var response = request(PATCH, PATH + "/" + invitation.internalInvitationId(), teacherToken,
                Map.of("status", "ACCEPTED"), SchoolInvitationResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(SchoolInvitationStatus.ACCEPTED);
        assertThat(response.getBody().respondedAt()).isNotNull();
        assertThat(linked(target.getId())).isTrue(); assertThat(linked(original.getId())).isTrue();
        assertThat(users.findById(teacher.getId()).orElseThrow().getSchool().getId()).isEqualTo(original.getId());
        assertThat(users.count()).isEqualTo(userCount); assertThat(registrations.count()).isEqualTo(registrationCount);
        assertThat(notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(teacher.getId()).getFirst().getReadAt()).isNotNull();
        assertThat(request(PATCH, "/api/me/active-school", teacherToken, Map.of("schoolId", target.getId()),
                UserResponse.class).getBody().schoolId()).isEqualTo(target.getId());
        var export = request(GET, "/api/me/export", teacherToken, null, UserExportResponse.class).getBody();
        assertThat(export.user().id()).isEqualTo(teacher.getId());
        assertThat(request(GET, "/api/me/export", teacherToken, null, String.class).getBody()).doesNotContain("schoolInvitations");
        assertThat(request(GET, PATH, teacherToken, null, String.class).getBody())
                .doesNotContain("invitedBy", "password", "token", coordinator.getEmail());
    }

    @Test
    void rejectDoesNotLinkAndAllowsNewInvitation() {
        var invitation = invite();
        var response = request(PATCH, PATH + "/" + invitation.internalInvitationId(), teacherToken,
                Map.of("status", "REJECTED"), SchoolInvitationResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(SchoolInvitationStatus.REJECTED);
        assertThat(linked(target.getId())).isFalse();
        assertThat(invite().internalInvitationId()).isEqualTo(invitation.internalInvitationId());
    }

    @Test
    void onlyRecipientCanSeeOrRespondAndAnonymousIsBlocked() throws Exception {
        var invitation = invite();
        var outsiderToken = autenticar(criarUsuario(Role.TEACHER));
        assertThat(request(GET, PATH, outsiderToken, null, SchoolInvitationResponse[].class).getBody()).isEmpty();
        assertThat(request(PATCH, PATH + "/" + invitation.internalInvitationId(), outsiderToken,
                Map.of("status", "ACCEPTED"), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(request(GET, PATH, null, null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(request(PATCH, PATH + "/" + invitation.internalInvitationId(), null,
                Map.of("status", "ACCEPTED"), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(linked(target.getId())).isFalse();
    }

    @Test
    void rejectsInvalidExpiredAndAlreadyAnsweredInvitations() {
        var invitation = invite(); String path = PATH + "/" + invitation.internalInvitationId();
        for (var body : List.of(Map.of("status", "PENDING"), Map.of("status", "INVALID"), Map.of())) {
            assertThat(request(PATCH, path, teacherToken, body, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(request(PATCH, PATH + "/9223372036854775807", teacherToken,
                Map.of("status", "ACCEPTED"), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        tx().executeWithoutResult(status -> invitations.findById(invitation.internalInvitationId()).orElseThrow()
                .setExpiresAt(Instant.EPOCH));
        assertThat(request(PATCH, path, teacherToken, Map.of("status", "ACCEPTED"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        invite();
        assertThat(request(PATCH, path, teacherToken, Map.of("status", "REJECTED"), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(PATCH, path, teacherToken, Map.of("status", "ACCEPTED"), String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(linked(target.getId())).isFalse();
    }

    @Test
    void duplicateSendsAndSimultaneousAnswersDoNotDuplicateMembership() {
        var invitation = invite();
        assertThat(request(POST, "/api/invitations/teachers", coordinatorToken,
                Map.of("email", teacher.getEmail()), String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var first = CompletableFuture.supplyAsync(() -> request(PATCH, PATH + "/" + invitation.internalInvitationId(),
                teacherToken, Map.of("status", "ACCEPTED"), String.class));
        var second = CompletableFuture.supplyAsync(() -> request(PATCH, PATH + "/" + invitation.internalInvitationId(),
                teacherToken, Map.of("status", "REJECTED"), String.class));
        assertThat(List.of(first.join().getStatusCode().value(), second.join().getStatusCode().value()))
                .containsExactlyInAnyOrder(200, 409);
        var status = invitations.findById(invitation.internalInvitationId()).orElseThrow().getStatus();
        assertThat(linked(target.getId())).isEqualTo(status == SchoolInvitationStatus.ACCEPTED);
    }

    @Test
    void acceptanceRevalidatesInviterWithoutDependingOnTheirSelectedSchool() {
        var invitation = invite();
        tx().executeWithoutResult(status -> {
            var sender = users.findById(coordinator.getId()).orElseThrow();
            sender.getSchools().add(original); sender.setActiveSchool(original);
        });
        assertThat(request(PATCH, PATH + "/" + invitation.internalInvitationId(), teacherToken,
                Map.of("status", "ACCEPTED"), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void inactiveInviterCannotGrantMembership() {
        var invitation = invite();
        tx().executeWithoutResult(status -> users.findById(coordinator.getId()).orElseThrow().setDeletedAt(Instant.now()));
        assertThat(request(PATCH, PATH + "/" + invitation.internalInvitationId(), teacherToken,
                Map.of("status", "ACCEPTED"), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(linked(target.getId())).isFalse();
    }

    @Test
    void teacherCannotSendAndExistingMembersOrNonTeachersCannotBeInvited() {
        assertThat(request(POST, "/api/invitations/teachers", teacherToken,
                Map.of("email", teacher.getEmail()), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var student = criarUsuario(Role.STUDENT);
        assertThat(request(POST, "/api/invitations/teachers", coordinatorToken,
                Map.of("email", student.getEmail()), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        tx().executeWithoutResult(status -> users.findById(teacher.getId()).orElseThrow().getSchools().add(target));
        assertThat(request(POST, "/api/invitations/teachers", coordinatorToken,
                Map.of("email", teacher.getEmail()), String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
