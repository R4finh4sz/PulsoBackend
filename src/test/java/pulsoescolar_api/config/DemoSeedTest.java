package pulsoescolar_api.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.entity.user.SchoolUser;
import pulsoescolar_api.repository.school.SchoolRepository;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.security.user.PasswordPolicy;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class DemoSeedTest {
    @Autowired SchoolRepository schools;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;

    DemoSeed seed(String password) {
        return new DemoSeed(schools, users, passwords, new PasswordPolicy(),
                new MockEnvironment().withProperty("DEMO_SEED_PASSWORD", password));
    }

    @Test void createsExactCountsAndLinksWithoutDuplicatingOnRepeat() {
        long originalSchools = schools.count();
        long originalUsers = users.count();
        var seed = seed("Demo-test-password-123");
        seed.run();
        seed.run();
        assertEquals(originalSchools + 10, schools.count());
        assertEquals(originalUsers + 1110, users.count());
        var demoUsers = users.findAll().stream().filter(u -> u.getRa().startsWith("DEMO-")).toList();
        assertEquals(10, demoUsers.stream().filter(u -> u.getRole() == Role.PEDAGOGICAL_COORDINATOR).count());
        assertEquals(100, demoUsers.stream().filter(u -> u.getRole() == Role.TEACHER).count());
        assertEquals(1000, demoUsers.stream().filter(u -> u.getRole() == Role.STUDENT).count());
        for (var school : schools.findAll().stream().filter(s -> s.getNome().startsWith("Escola Demonstração ")).toList()) {
            var members = demoUsers.stream().filter(u -> u.getSchool().getId().equals(school.getId())).toList();
            assertEquals(111, members.size());
            assertEquals(1, members.stream().filter(u -> u.getRole() == Role.PEDAGOGICAL_COORDINATOR).count());
            assertEquals(10, members.stream().filter(u -> u.getRole() == Role.TEACHER).count());
            assertEquals(100, members.stream().filter(u -> u.getRole() == Role.STUDENT).count());
        }
        for (var user : demoUsers) {
            assertTrue(user.belongsToSchool(user.getSchool().getId()));
            assertEquals(user.getSchool(), user.getActiveSchool());
            assertNull(user.getClassroom());
            assertFalse(user.isTermsAccepted());
        }
        assertTrue(passwords.matches("Demo-test-password-123", demoUsers.getFirst().getPasswordHash()));
    }

    @Test void refusesWeakPasswordBeforeWriting() {
        long before = schools.count();
        assertThrows(RuntimeException.class, () -> seed("weak").run());
        assertEquals(before, schools.count());
        assertEquals(0, users.countByRaStartingWith("DEMO-"));
    }

    @Test void refusesPartialSeedWithoutAddingData() {
        var user = new SchoolUser();
        user.setFullName("Partial demo");
        user.setRa("DEMO-partial");
        user.setEmail("partial@example.invalid");
        user.setRole(Role.STUDENT);
        user.setPasswordHash("unused");
        users.saveAndFlush(user);
        long before = schools.count();
        assertThrows(IllegalStateException.class, () -> seed("Demo-test-password-123").run());
        assertEquals(before, schools.count());
        assertEquals(1, users.countByRaStartingWith("DEMO-"));
    }
}
