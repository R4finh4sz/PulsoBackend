package pulsoescolar_api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pulsoescolar_api.dto.school.CreateSchoolRequest;
import pulsoescolar_api.dto.terms.*;
import pulsoescolar_api.dto.user.*;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.security.CurrentUser;
import pulsoescolar_api.service.audit.*;
import pulsoescolar_api.service.mail.WelcomeMailService;
import pulsoescolar_api.service.school.SchoolService;
import pulsoescolar_api.service.terms.TermsService;
import pulsoescolar_api.service.user.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
class AuditTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired AuditService audit;
    @Autowired SchoolService schools;
    @Autowired TermsService terms;
    @Autowired UserRegistrationService registration;
    @Autowired AccountDeletionService deletion;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean CurrentUser currentUser;
    @MockitoBean WelcomeMailService mail;

    private SchoolUser actor(Role role) {
        var user = new SchoolUser();
        user.setFullName("Private name");
        user.setRa("private-" + role);
        user.setEmail(role + "@private.test");
        user.setPasswordHash("hash");
        user.setRole(role);
        users.saveAndFlush(user);
        when(currentUser.get()).thenReturn(user);
        return user;
    }

    private long count(AuditEvent event) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE event_type = ?",
                Long.class, event.code());
    }

    @Test @Transactional
    void recordsSchoolAndOnlyCoordinatorRegistration() {
        var admin = actor(Role.ADMIN);
        var school = schools.create(new CreateSchoolRequest("Private school", "12345678000190", "Street", "Area", "City"));
        assertEquals(1, count(AuditEvent.SCHOOL_CREATED));
        for (Role role : new Role[]{Role.STUDENT, Role.TEACHER, Role.PEDAGOGICAL_COORDINATOR}) {
            registration.createUser(new CreateUser("Private name", role.name(), role + "@new.test", school.id()), role);
        }
        assertEquals(1, count(AuditEvent.COORDINATOR_CREATED));
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Long.class));
        assertEquals(java.util.List.of(admin.getId(), admin.getId()), jdbc.queryForList(
                "SELECT actor_user_id FROM audit_events", Long.class));
        assertEquals("SCHOOL_CREATED", jdbc.queryForObject(
                "SELECT event_name FROM audit_events_grafana WHERE event_name = 'SCHOOL_CREATED'", String.class));
        assertEquals(admin.getId(), jdbc.queryForObject(
                "SELECT actor_user_id FROM audit_events_grafana WHERE event_name = 'SCHOOL_CREATED'", Long.class));
    }

    @Test @Transactional
    void termsAcceptanceIsIdempotentAndInvalidVersionDoesNotLog() {
        var admin = actor(Role.ADMIN);
        var term = terms.publish(new TermsRequest("Terms", "Content"), true);
        assertThrows(RuntimeException.class, () -> terms.accept(new AcceptTermsRequest("invalid", true)));
        assertEquals(0, count(AuditEvent.TERMS_ACCEPTED));
        terms.accept(new AcceptTermsRequest(term.version(), true));
        terms.accept(new AcceptTermsRequest(term.version(), true));
        assertEquals(1, count(AuditEvent.TERMS_ACCEPTED));
        assertEquals(admin.getId(), jdbc.queryForObject(
                "SELECT actor_user_id FROM audit_events WHERE event_type = 1", Long.class));
        var next = terms.publish(new TermsRequest("Terms", "New content"), false);
        terms.accept(new AcceptTermsRequest(next.version(), true));
        assertEquals(2, count(AuditEvent.TERMS_ACCEPTED));
    }

    @Test @Transactional
    void logsOnlyApprovedDeletionAndDoesNotLogRepeatedReview() {
        var student = actor(Role.STUDENT);
        var first = deletion.create(new CreateDeletionRequest("Private reason"));
        var admin = actor(Role.ADMIN);
        deletion.review(first.id(), new ReviewDeletionRequest(DeletionStatus.REJECTED, "Private reason"));
        assertEquals(0, count(AuditEvent.ACCOUNT_DELETED));
        when(currentUser.get()).thenReturn(student);
        var second = deletion.create(new CreateDeletionRequest("Private reason"));
        when(currentUser.get()).thenReturn(admin);
        deletion.review(second.id(), new ReviewDeletionRequest(DeletionStatus.APPROVED, "Private reason"));
        assertEquals(1, count(AuditEvent.ACCOUNT_DELETED));
        assertEquals(admin.getId(), jdbc.queryForObject(
                "SELECT actor_user_id FROM audit_events WHERE event_type = 2", Long.class));
        assertThrows(RuntimeException.class, () -> deletion.review(second.id(),
                new ReviewDeletionRequest(DeletionStatus.APPROVED, "Again")));
        assertEquals(1, count(AuditEvent.ACCOUNT_DELETED));
    }

    @Test
    void rollbackRemovesAuditAndBusinessRowsTogether() {
        long before = count(AuditEvent.SCHOOL_CREATED);
        long schoolsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM schools", Long.class);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            actor(Role.ADMIN);
            schools.create(new CreateSchoolRequest("Private school", "12345678000190", "Street", "Area", "City"));
            assertEquals(before + 1, count(AuditEvent.SCHOOL_CREATED));
            status.setRollbackOnly();
        });
        assertEquals(before, count(AuditEvent.SCHOOL_CREATED));
        assertEquals(schoolsBefore, jdbc.queryForObject("SELECT COUNT(*) FROM schools", Long.class));
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                () -> audit.record(AuditEvent.SCHOOL_CREATED));
    }

    @Test @Transactional
    void schemaIncludesActorAllowsLegacyRowsAndRejectsUnknownCodes() {
        var columns = jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = 'AUDIT_EVENTS'", String.class);
        assertEquals(java.util.Set.of("OCCURRED_AT", "EVENT_TYPE", "ACTOR_USER_ID"), new java.util.HashSet<>(columns));
        jdbc.update("INSERT INTO audit_events (occurred_at, event_type) VALUES (CURRENT_TIMESTAMP, 1)");
        assertNull(jdbc.queryForObject("SELECT actor_user_id FROM audit_events WHERE event_type = 1", Long.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO audit_events (occurred_at, event_type) VALUES (CURRENT_TIMESTAMP, 99)"));
    }
}
