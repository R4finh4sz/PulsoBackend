package pulsoescolar_api;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pulsoescolar_api.dto.auth.LoginRequest;
import pulsoescolar_api.dto.auth.LoginResponse;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.auth.AuthSessionRepository;
import pulsoescolar_api.repository.user.UserRepository;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:jwt;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class JwtAuthenticationTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired AuthSessionRepository sessions;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtDecoder decoder;
    @Autowired JwtEncoder encoder;
    @MockitoBean Clock clock;
    @MockitoBean pulsoescolar_api.service.mail.TwoFactorMailService twoFactorMail;
    MockMvc mvc;
    Instant now;
    Long userId;

    @BeforeEach void setup() {
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        when(clock.instant()).thenReturn(now);
        users.deleteAll();
        jdbc.update("DELETE FROM terms_versions");
        jdbc.update("UPDATE terms_of_use SET version = 0, title = NULL, content = NULL");
        var user = new SchoolUser();
        user.setFullName("Admin"); user.setRa("admin"); user.setEmail("admin@example.com");
        user.setRole(Role.ADMIN); user.setPasswordHash(passwords.encode("admin-password-123"));
        userId = users.saveAndFlush(user).getId();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    String login() throws Exception {
        org.mockito.Mockito.clearInvocations(twoFactorMail);
        var result = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"ADMIN@example.com\",\"password\":\"admin-password-123\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.termsAccepted").value(true))
                .andExpect(jsonPath("$.user.firstLogin").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.user.id").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.user.fullName").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.user.ra").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.user.email").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist()).andReturn();
        assertNull(result.getRequest().getSession(false));
        String token = JsonMapper.builder().build().readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
        var code = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(twoFactorMail, org.mockito.Mockito.timeout(3000)).send(org.mockito.ArgumentMatchers.eq("admin@example.com"), code.capture());
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/2fa/verify").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType("application/json").content("{\"code\":\"" + code.getValue() + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/2fa/verify").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType("application/json").content("{\"code\":\"" + code.getValue() + "\"}"))
                .andExpect(status().isForbidden());
        return token;
    }

    void unauthorized(String token) throws Exception {
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    String sign(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test void loginCreatesSessionAndLogoutRevokesOnlyThatSession() throws Exception {
        String first = login();
        String second = login();
        assertNotEquals(first, second);
        var jwt = decoder.decode(first);
        assertEquals(userId.toString(), jwt.getSubject());
        assertEquals(now.plusSeconds(1800), jwt.getExpiresAt());
        assertTrue(sessions.isActive(UUID.fromString(jwt.getId()), userId, now));
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + first))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + first))
                .andExpect(status().isNoContent());
        assertFalse(sessions.isActive(UUID.fromString(jwt.getId()), userId, now));
        unauthorized(first);
        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + first))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + second))
                .andExpect(status().isOk());
    }

    @Test void rejectsExpiredTokenAtSessionDeadline() throws Exception {
        String token = login();
        when(clock.instant()).thenReturn(now.plusSeconds(1800));
        unauthorized(token);
    }

    @Test void twoFactorResendCooldownExpiryAndAttemptLimit() throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"admin@example.com\",\"password\":\"admin-password-123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.twoFactorRequired").value(true)).andReturn();
        String token = JsonMapper.builder().build().readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
        var code = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(twoFactorMail, org.mockito.Mockito.timeout(3000)).send(org.mockito.ArgumentMatchers.anyString(), code.capture());
        String original = code.getValue();
        for (String path : List.of("/api/me", "/api/terms", "/api/terms/history", "/api/terms/accepted")) {
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isForbidden());
        }
        mvc.perform(patch("/api/auth/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"admin@example.com\",\"password\":\"admin-password-123\"}"))
                .andExpect(status().isTooManyRequests());
        when(clock.instant()).thenReturn(now.plusSeconds(179));
        mvc.perform(post("/api/auth/2fa/resend").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isTooManyRequests());
        when(clock.instant()).thenReturn(now.plusSeconds(180));
        org.mockito.Mockito.clearInvocations(twoFactorMail);
        mvc.perform(post("/api/auth/2fa/resend").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(twoFactorMail, org.mockito.Mockito.timeout(3000)).send(org.mockito.ArgumentMatchers.anyString(), code.capture());
        String current = code.getValue();
        var stored = sessions.findById(UUID.fromString(decoder.decode(token).getId())).orElseThrow();
        assertNotEquals(current, stored.getCodeHash());
        assertTrue(passwords.matches(current, stored.getCodeHash()));
        if (!original.equals(current)) {
            assertFalse(passwords.matches(original, stored.getCodeHash()));
        }
        String wrong = current.equals("000000") ? "111111" : "000000";
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/api/auth/2fa/verify").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType("application/json").content("{\"code\":\"" + wrong + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/2fa/verify").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType("application/json").content("{\"code\":\"" + current + "\"}"))
                .andExpect(status().isBadRequest());
        when(clock.instant()).thenReturn(now.plusSeconds(360));
        org.mockito.Mockito.clearInvocations(twoFactorMail);
        mvc.perform(post("/api/auth/2fa/resend").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(twoFactorMail, org.mockito.Mockito.timeout(3000)).send(org.mockito.ArgumentMatchers.anyString(), code.capture());
        when(clock.instant()).thenReturn(now.plusSeconds(960));
        mvc.perform(post("/api/auth/2fa/verify").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType("application/json").content("{\"code\":\"" + code.getValue() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void mailFailureLeavesSessionPendingAndAllowsLaterResend() throws Exception {
        org.mockito.Mockito.doThrow(new pulsoescolar_api.exception.EmailDeliveryException())
                .when(twoFactorMail).send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        var result = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"admin@example.com\",\"password\":\"admin-password-123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.twoFactorRequired").value(true)).andReturn();
        org.mockito.Mockito.verify(twoFactorMail, org.mockito.Mockito.timeout(3000))
                .send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        assertEquals(1, sessions.count());
        String token = JsonMapper.builder().build().readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.doNothing().when(twoFactorMail)
                .send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        when(clock.instant()).thenReturn(now.plusSeconds(180));
        mvc.perform(post("/api/auth/2fa/resend").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        var code = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(twoFactorMail, org.mockito.Mockito.timeout(3000).times(2))
                .send(org.mockito.ArgumentMatchers.anyString(), code.capture());
        mvc.perform(post("/api/auth/2fa/verify").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType("application/json").content("{\"code\":\"" + code.getValue() + "\"}"))
                .andExpect(status().isNoContent());
    }

    @Test void slowMailDoesNotBlockLoginAndOnlyStartsAfterCommit() throws Exception {
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var finished = new java.util.concurrent.CountDownLatch(1);
        var committed = new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.Mockito.doAnswer(invocation -> {
            try {
                committed.set(sessions.count() == 1);
                started.countDown();
                release.await(10, java.util.concurrent.TimeUnit.SECONDS);
                return null;
            } finally {
                finished.countDown();
            }
        }).when(twoFactorMail).send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        try {
            var result = assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () ->
                    mvc.perform(post("/api/auth/login").contentType("application/json")
                            .content("{\"email\":\"admin@example.com\",\"password\":\"admin-password-123\"}"))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.twoFactorRequired").value(true)).andReturn());
            assertTrue(started.await(3, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(committed.get());
            assertEquals(1, finished.getCount(), "SMTP is still blocked when login returns");
            String token = JsonMapper.builder().build().readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
            mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden());
        } finally {
            release.countDown();
            assertTrue(finished.await(3, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired org.springframework.context.ApplicationEventPublisher events;

    @Test void rollbackDoesNotSendCode() {
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(new pulsoescolar_api.service.mail.TwoFactorMailRequested("admin@example.com", "123456"));
            status.setRollbackOnly();
        });
        org.mockito.Mockito.verifyNoInteractions(twoFactorMail);
    }

    @Test void termsRequireAdminAndNewVersionsResetAcceptance() throws Exception {
        String token = login();
        String body = "{\"title\":\"Termos de uso\",\"content\":\"Texto dos termos\"}";
        mvc.perform(post("/api/terms").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/terms").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/terms").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value("1.0"));
        mvc.perform(post("/api/terms").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(post("/api/terms/accept").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType("application/json").content("{\"version\":\"1.0\",\"termsAccepted\":true}"))
                .andExpect(status().isNoContent());
        assertTrue(users.findById(userId).orElseThrow().isTermsAccepted());
        mvc.perform(post("/api/terms/accept").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType("application/json").content("{\"version\":\"1.0\",\"termsAccepted\":true}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/terms/accepted").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().json("[\"1.0\"]"));
        mvc.perform(put("/api/terms").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType("application/json").content("{\"title\":\"Novo titulo\",\"content\":\"Novo texto\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value("1.1"));
        mvc.perform(get("/api/terms/history").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").value("1.0"))
                .andExpect(jsonPath("$[0].title").value("Termos de uso"))
                .andExpect(jsonPath("$[0].content").value("Texto dos termos"))
                .andExpect(jsonPath("$[1].version").value("1.1"))
                .andExpect(jsonPath("$[1].title").value("Novo titulo"))
                .andExpect(jsonPath("$[1].content").value("Novo texto"));
        assertFalse(users.findById(userId).orElseThrow().isTermsAccepted());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"admin@example.com\",\"password\":\"admin-password-123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.termsAccepted").value(true));
        mvc.perform(post("/api/terms/accept").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType("application/json").content("{\"version\":\"1.0\",\"termsAccepted\":true}"))
                .andExpect(status().isConflict());
        for (Role role : List.of(Role.STUDENT, Role.TEACHER, Role.PEDAGOGICAL_COORDINATOR)) {
            var user = users.findById(userId).orElseThrow();
            user.setRole(role);
            users.saveAndFlush(user);
            mvc.perform(post("/api/terms").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType("application/json").content(body)).andExpect(status().isForbidden());
            mvc.perform(put("/api/terms").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType("application/json").content(body)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/terms").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value("1.1"));
        mvc.perform(post("/api/terms/accept").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType("application/json").content("{\"version\":\"1.1\",\"termsAccepted\":false}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/terms/accept").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType("application/json").content("{\"version\":\"1.1\",\"termsAccepted\":true}"))
                .andExpect(status().isNoContent());
        assertTrue(users.findById(userId).orElseThrow().isTermsAccepted());
        mvc.perform(get("/api/terms/accepted").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().json("[\"1.0\",\"1.1\"]"));
    }

    @Test void optionalPasswordChangeDoesNotAcceptTerms() throws Exception {
        String token = login();
        mvc.perform(post("/api/terms").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType("application/json").content("{\"title\":\"Inicial\",\"content\":\"Texto inicial\"}"))
                .andExpect(status().isCreated());
        String body = "{\"currentPassword\":\"admin-password-123\",\"newPassword\":\"new-password-456\"}";
        mvc.perform(patch("/api/auth/password").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/auth/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType("application/json").content(body))
                .andExpect(status().isNoContent());
        var changed = users.findById(userId).orElseThrow();
        assertFalse(changed.isTermsAccepted());
        assertTrue(passwords.matches("new-password-456", changed.getPasswordHash()));
        mvc.perform(get("/api/terms/accepted").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(post("/api/terms/accept").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType("application/json").content("{\"version\":\"1.0\",\"termsAccepted\":true}"))
                .andExpect(status().isNoContent());
        assertTrue(users.findById(userId).orElseThrow().isTermsAccepted());
    }
    @Test void versionsContinueAfterNineEditsAndAcceptanceIsPrivate() throws Exception {
        String token = login();
        String body = "{\"title\":\"Termo\",\"content\":\"Conteudo\"}";
        mvc.perform(post("/api/terms").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType("application/json").content(body)).andExpect(status().isCreated());
        for (int i = 1; i <= 10; i++) {
            mvc.perform(put("/api/terms").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType("application/json").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.version").value("1." + i));
        }
        mvc.perform(post("/api/terms/accept").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType("application/json").content("{\"version\":\"1.10\",\"termsAccepted\":true}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/terms/accepted").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().json("[\"1.10\"]"));
        mvc.perform(get("/api/terms/accepted")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/terms/history")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"admin@example.com\",\"password\":\"admin-password-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.termsAcceptedVersions[0]").value("1.10"));
    }

    @Test void rejectsExpiredJwtEvenWhenSessionIsStillActive() throws Exception {
        var jwt = decoder.decode(login());
        unauthorized(sign(JwtClaimsSet.builder().claims(c -> c.putAll(jwt.getClaims()))
                .issuedAt(now.minusSeconds(120)).expiresAt(now.minusSeconds(60)).build()));
        assertTrue(sessions.isActive(UUID.fromString(jwt.getId()), userId, now));
    }

    @Test void rejectsMalformedTamperedAndWronglySignedTokens() throws Exception {
        String token = login();
        unauthorized("not-a-jwt");
        int signatureStart = token.lastIndexOf('.') + 1;
        unauthorized(token.substring(0, signatureStart)
                + (token.charAt(signatureStart) == 'A' ? 'B' : 'A') + token.substring(signatureStart + 1));
        var otherEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(new byte[32], "HmacSHA256")));
        unauthorized(otherEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().claims(c -> c.putAll(decoder.decode(token).getClaims())).build())).getTokenValue());
    }

    @Test void rejectsWrongIssuerAudienceAndMissingRequiredClaims() throws Exception {
        var jwt = decoder.decode(login());
        var base = jwt.getClaims();
        unauthorized(sign(JwtClaimsSet.builder().claims(c -> c.putAll(base)).issuer("other-api").build()));
        unauthorized(sign(JwtClaimsSet.builder().claims(c -> c.putAll(base)).audience(List.of("other-client")).build()));
        for (String claim : List.of("exp", "iat", "sub", "jti")) {
            unauthorized(sign(JwtClaimsSet.builder().claims(c -> { c.putAll(base); c.remove(claim); }).build()));
        }
        unauthorized(sign(JwtClaimsSet.builder().claims(c -> c.putAll(base)).subject("invalid-id").build()));
        unauthorized(sign(JwtClaimsSet.builder().claims(c -> c.putAll(base)).id("invalid-id").build()));
    }

    @Test void rejectsSignedTokenWithoutSessionOrForAnotherUser() throws Exception {
        var jwt = decoder.decode(login());
        unauthorized(sign(JwtClaimsSet.builder().claims(c -> c.putAll(jwt.getClaims()))
                .id(UUID.randomUUID().toString()).build()));
        unauthorized(sign(JwtClaimsSet.builder().claims(c -> c.putAll(jwt.getClaims()))
                .subject(Long.toString(userId + 1)).build()));
        users.deleteById(userId);
        unauthorized(jwt.getTokenValue());
    }

    @Test void identityAndPermissionsFollowCurrentUserRecord() throws Exception {
        String token = login();
        var user = users.findById(userId).orElseThrow();
        user.setEmail("renamed@example.com");
        user.setRole(Role.STUDENT);
        users.saveAndFlush(user);
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("renamed@example.com"));
        mvc.perform(get("/api/students").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test void requiresBearerHeaderAndDoesNotAcceptLegacyAuthentication() throws Exception {
        String token = login();
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").with(httpBasic("admin@example.com", "admin-password-123")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").param("access_token", token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(new jakarta.servlet.http.Cookie("access_token", token)))
                .andExpect(status().isUnauthorized());
        var oldContext = SecurityContextHolder.createEmptyContext();
        oldContext.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("admin@example.com", null, List.of()));
        var oldSession = new MockHttpSession();
        oldSession.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, oldContext);
        mvc.perform(get("/api/me").session(oldSession)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/csrf").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test void rejectsInvalidCredentialsWithoutCsrfOrLeakingSecrets() throws Exception {
        for (String email : List.of("admin@example.com", "missing@example.com")) {
            mvc.perform(post("/api/auth/login").contentType("application/json")
                    .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
        }
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        assertFalse(new LoginRequest("admin@example.com", "secret").toString().contains("secret"));
        assertFalse(new LoginResponse("secret", "Bearer", now, null, true, now, now).toString().contains("secret"));
    }
}
