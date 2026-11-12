package pulsoescolar_api.integration.user;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.entity.auth.AuthSession;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.auth.AuthSessionRepository;
import pulsoescolar_api.repository.user.*;
import pulsoescolar_api.service.user.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class AutomaticAccountDeletionTests {
    static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");
    @MockitoBean Clock clock;
    @Autowired AccountDeletionService service;
    @Autowired AutomaticAccountDeletionJob job;
    @Autowired AccountDeletionRepository requests;
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    @BeforeEach void setup() { when(clock.instant()).thenReturn(NOW); }

    AccountDeletionRequest request(Role role, Instant requestedAt, DeletionStatus status) {
        var user = new SchoolUser();
        var unique = UUID.randomUUID().toString();
        user.setFullName("Nome Original"); user.setRa(unique); user.setEmail(unique + "@example.com");
        user.setPasswordHash("old-hash"); user.setRole(role);
        user.setBirthDate(LocalDate.of(2000, 1, 1)); user.setProfilePhoto(new byte[]{1});
        user.setTermsAccepted(true);
        users.saveAndFlush(user);
        var request = new AccountDeletionRequest();
        request.setRequester(user); request.setReason("Motivo pessoal");
        request.setRequestedAt(requestedAt); request.setStatus(status);
        return requests.saveAndFlush(request);
    }

    @Test void approvesAtExactlyFifteenDaysWithoutAuthenticatedUserAndRevokesSessions() {
        long previousEvents = jdbc.queryForObject("select count(*) from audit_events where event_type=2 and actor_user_id is null", Long.class);
        var request = request(Role.STUDENT, NOW.minus(15, ChronoUnit.DAYS), DeletionStatus.PENDING);
        var user = request.getRequester();
        var session = new AuthSession();
        session.setId(UUID.randomUUID()); session.setUser(user); session.setCreatedAt(NOW);
        session.setExpiresAt(NOW.plusSeconds(600)); session.setTwoFactorVerified(true);
        sessions.saveAndFlush(session);
        job.process();
        assertEquals(DeletionStatus.APPROVED, request.getStatus());
        assertTrue(request.isAutomatic()); assertNull(request.getReviewedBy());
        assertEquals(NOW, request.getReviewedAt()); assertEquals(NOW, user.getDeletedAt());
        assertEquals("DeletedUser_" + user.getId(), user.getFullName());
        assertEquals(user.getFullName(), user.getRa());
        assertTrue(user.getEmail().endsWith("@deleted.invalid"));
        assertNull(user.getBirthDate()); assertNull(user.getProfilePhoto());
        assertFalse(user.isTermsAccepted()); assertNotEquals("old-hash", user.getPasswordHash());
        assertFalse(sessions.existsById(session.getId()));
        assertEquals(user.getFullName(), request.getReason());
        assertTrue(request.getReviewReason().contains("15 dias"));
        assertTrue(pulsoescolar_api.dto.user.DeletionResponse.from(request).automatic());
        assertEquals(previousEvents + 1, jdbc.queryForObject("select count(*) from audit_events where event_type=2 and actor_user_id is null", Long.class));
        service.approveExpired(request.getId());
        assertEquals(previousEvents + 1, jdbc.queryForObject("select count(*) from audit_events where event_type=2 and actor_user_id is null", Long.class));
    }

    @Test void doesNotApproveBeforeDeadlineOrAfterManualDecision() {
        for (var status : DeletionStatus.values()) {
            var request = request(Role.TEACHER, status == DeletionStatus.PENDING
                    ? NOW.minus(15, ChronoUnit.DAYS).plusSeconds(1) : NOW.minus(16, ChronoUnit.DAYS), status);
            service.approveExpired(request.getId());
            assertEquals(status, request.getStatus()); assertFalse(request.isAutomatic());
            assertNull(request.getRequester().getDeletedAt());
        }
    }

    @Test void processesOverdueTeachersAndCoordinatorsButNeverAdministrators() {
        for (var role : Role.values()) {
            var request = request(role, NOW.minus(16, ChronoUnit.DAYS), DeletionStatus.PENDING);
            service.approveExpired(request.getId());
            assertEquals(role != Role.ADMIN, request.isAutomatic());
            assertEquals(role == Role.ADMIN ? DeletionStatus.PENDING : DeletionStatus.APPROVED, request.getStatus());
        }
    }

    @Test void queryExcludesNotDueDecidedAndDeletedAccounts() {
        var due = request(Role.STUDENT, NOW.minus(15, ChronoUnit.DAYS), DeletionStatus.PENDING);
        request(Role.STUDENT, NOW.minus(14, ChronoUnit.DAYS), DeletionStatus.PENDING);
        request(Role.TEACHER, NOW.minus(16, ChronoUnit.DAYS), DeletionStatus.REJECTED);
        request(Role.ADMIN, NOW.minus(16, ChronoUnit.DAYS), DeletionStatus.PENDING);
        var deleted = request(Role.STUDENT, NOW.minus(16, ChronoUnit.DAYS), DeletionStatus.PENDING);
        deleted.getRequester().setDeletedAt(NOW); users.flush();
        assertEquals(java.util.List.of(due.getId()), requests.findDueIds(NOW.minus(15, ChronoUnit.DAYS)));
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void manualAndAutomaticApprovalProcessTheAccountOnlyOnce() throws Exception {
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        long previousEvents = jdbc.queryForObject("select count(*) from audit_events where event_type=2", Long.class);
        var pending = tx.execute(status -> request(Role.STUDENT, NOW.minus(16, ChronoUnit.DAYS), DeletionStatus.PENDING));
        var admin = tx.execute(status -> request(Role.ADMIN, NOW, DeletionStatus.REJECTED));
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var automatic = executor.submit(() -> { start.await(); service.approveExpired(pending.getId()); return null; });
            var manual = executor.submit(() -> {
                start.await();
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                admin.getRequester().getEmail(), "", java.util.List.of()));
                try {
                    service.review(pending.getId(), new pulsoescolar_api.dto.user.ReviewDeletionRequest(DeletionStatus.APPROVED, "Aprovado"));
                } catch (pulsoescolar_api.exception.common.BusinessConflictException alreadyProcessed) {
                    // A rotina automática pode vencer a disputa.
                } finally {
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
                return null;
            });
            start.countDown();
            automatic.get(15, java.util.concurrent.TimeUnit.SECONDS);
            manual.get(15, java.util.concurrent.TimeUnit.SECONDS);
            tx.executeWithoutResult(status -> {
                var reviewed = requests.findById(pending.getId()).orElseThrow();
                assertEquals(DeletionStatus.APPROVED, reviewed.getStatus());
                assertNotNull(reviewed.getRequester().getDeletedAt());
            });
            assertEquals(previousEvents + 1, jdbc.queryForObject("select count(*) from audit_events where event_type=2", Long.class));
        } finally {
            tx.executeWithoutResult(status -> {
                requests.deleteById(pending.getId()); requests.deleteById(admin.getId()); requests.flush();
                users.deleteById(pending.getRequester().getId()); users.deleteById(admin.getRequester().getId());
            });
        }
    }
}
