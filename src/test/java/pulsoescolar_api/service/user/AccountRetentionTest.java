package pulsoescolar_api.service.user;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.entity.school.School;
import static org.assertj.core.api.Assertions.assertThat;

class AccountRetentionTest {
    private SchoolUser account(Role role) {
        var user = new SchoolUser();
        user.setRole(role);
        user.setCreatedAt(Instant.parse("2026-01-01T03:00:00Z"));
        return user;
    }

    @Test void requiresSixCalendarMonthsAndNoActiveInstitutionForEveryNonAdminRole() {
        var now = Instant.parse("2026-07-01T03:00:00Z");
        for (var role : new Role[]{Role.STUDENT, Role.TEACHER, Role.PEDAGOGICAL_COORDINATOR}) {
            var user = account(role);
            assertThat(AccountDeletionService.retentionDue(user, null, now.minusNanos(1))).isFalse();
            assertThat(AccountDeletionService.retentionDue(user, null, now)).isTrue();
            var school = new School(); school.setActive(true);
            user.setSchool(school);
            assertThat(AccountDeletionService.retentionDue(user, null, now)).isFalse();
            school.setActive(false);
            assertThat(AccountDeletionService.retentionDue(user, null, now)).isTrue();
            user.setLastAuthenticatedAt(now.minusSeconds(1));
            assertThat(AccountDeletionService.retentionDue(user, null, now)).isFalse();
        }
    }

    @Test void anyActiveTeacherMembershipProtectsAccount() {
        var user = account(Role.TEACHER);
        var school = new School(); school.setActive(true);
        user.getSchools().add(school);
        assertThat(AccountDeletionService.retentionDue(user, null, Instant.parse("2027-01-01T03:00:00Z"))).isFalse();
    }

    @Test void inactiveSchoolMembershipDoesNotProtectInactiveAccount() {
        var user = account(Role.TEACHER);
        var school = new School(); school.setActive(false);
        user.getSchools().add(school);
        assertThat(AccountDeletionService.retentionDue(user, null, Instant.parse("2027-01-01T03:00:00Z"))).isTrue();
    }

    @Test void graduationWaitsUntilJanuary31InSaoPauloEvenWithRecentActivity() {
        var user = account(Role.STUDENT);
        var graduation = Instant.parse("2026-12-15T12:00:00Z");
        var deadline = Instant.parse("2027-01-31T03:00:00Z");
        user.setLastAuthenticatedAt(deadline);
        assertThat(AccountDeletionService.retentionDue(user, graduation, deadline.minusNanos(1))).isFalse();
        assertThat(AccountDeletionService.retentionDue(user, graduation, deadline)).isTrue();
        user.setDeletedAt(deadline);
        assertThat(AccountDeletionService.retentionDue(user, graduation, deadline)).isFalse();
        assertThat(AccountDeletionService.retentionDue(account(Role.ADMIN), null, deadline)).isFalse();
    }

    @Test void endOfMonthUsesCalendarMonths() {
        var user = account(Role.STUDENT);
        user.setLastAuthenticatedAt(Instant.parse("2026-08-31T03:00:00Z"));
        assertThat(AccountDeletionService.retentionDue(user, null, Instant.parse("2027-02-28T03:00:00Z"))).isTrue();
    }
}
