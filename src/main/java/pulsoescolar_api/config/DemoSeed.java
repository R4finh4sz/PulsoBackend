package pulsoescolar_api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.entity.user.SchoolUser;
import pulsoescolar_api.repository.school.SchoolRepository;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.security.user.PasswordPolicy;

@Component
@Profile("demo-seed")
@RequiredArgsConstructor
public class DemoSeed implements CommandLineRunner {
    private final SchoolRepository schools;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final PasswordPolicy passwordPolicy;
    private final Environment environment;

    @Override
    @Transactional
    public void run(String... args) {
        long existing = users.countByRaStartingWith("DEMO-");
        if (existing == 1110) return;
        if (existing != 0) throw new IllegalStateException("Carga DEMO parcial encontrada. Revise os dados antes de continuar.");
        String password = environment.getRequiredProperty("DEMO_SEED_PASSWORD");
        passwordPolicy.requireValidNewPassword(password);
        String hash = passwords.encode(password);
        for (int schoolNumber = 1; schoolNumber <= 10; schoolNumber++) {
            var school = new School();
            school.setNome("Escola Demonstração " + schoolNumber);
            school.setLogradouro("Praça da Sé");
            school.setBairro("Sé");
            school.setCidade("São Paulo");
            school.setCep("01001000");
            school.setUf("SP");
            schools.save(school);
            createUser(school, schoolNumber, Role.PEDAGOGICAL_COORDINATOR, "coordenador", 1, hash);
            for (int i = 1; i <= 10; i++) createUser(school, schoolNumber, Role.TEACHER, "professor", i, hash);
            for (int i = 1; i <= 100; i++) createUser(school, schoolNumber, Role.STUDENT, "aluno", i, hash);
        }
        users.flush();
    }

    private void createUser(School school, int schoolNumber, Role role, String label, int number, String hash) {
        var user = new SchoolUser();
        String identifier = schoolNumber + "-" + label + "-" + number;
        user.setFullName("Demo " + label + " " + schoolNumber + "-" + number);
        user.setRa("DEMO-" + identifier);
        user.setEmail(identifier + "@example.invalid");
        user.setRole(role);
        user.setSchool(school);
        user.setPasswordHash(hash);
        // Demo accounts must accept the real terms at login; no consent is fabricated.
        user.setTermsAccepted(false);
        users.save(user);
    }
}
