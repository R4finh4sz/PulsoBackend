package pulsoescolar_api.config;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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
    private static final ZoneId SEED_ZONE = ZoneId.of("America/Sao_Paulo");

    private record Location(String city, String state) {}

    private static final List<Location> ADDITIONAL_LOCATIONS = List.of(
            new Location("Rio de Janeiro", "RJ"),
            new Location("Belo Horizonte", "MG"),
            new Location("Curitiba", "PR"),
            new Location("Florianópolis", "SC"),
            new Location("Porto Alegre", "RS"),
            new Location("Salvador", "BA"),
            new Location("Recife", "PE"),
            new Location("Fortaleza", "CE"),
            new Location("Goiânia", "GO"),
            new Location("Belém", "PA"));

    private final SchoolRepository schools;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final PasswordPolicy passwordPolicy;
    private final Environment environment;

    @Override
    @Transactional
    public void run(String... args) {
        long existing = users.countByRaStartingWith("DEMO-");
        if (existing == 5000) return;
        if (existing != 0 && existing != 1110) {
            throw new IllegalStateException("Carga DEMO parcial encontrada. Revise os dados antes de continuar.");
        }
        String password = environment.getRequiredProperty("DEMO_SEED_PASSWORD");
        passwordPolicy.requireValidNewPassword(password);
        String hash = passwords.encode(password);
        LocalDate yesterday = LocalDate.now(SEED_ZONE).minusDays(1);
        int firstSchool = existing == 0 ? 1 : 11;
        for (int schoolNumber = firstSchool; schoolNumber <= 10 + ADDITIONAL_LOCATIONS.size(); schoolNumber++) {
            var school = new School();
            school.setNome("Escola Demonstração " + schoolNumber);
            boolean original = schoolNumber <= 10;
            if (original) {
                school.setLogradouro("Praça da Sé");
                school.setBairro("Sé");
                school.setCidade("São Paulo");
                school.setCep("01001000");
                school.setUf("SP");
            } else {
                var location = ADDITIONAL_LOCATIONS.get(schoolNumber - 11);
                school.setLogradouro("Rua da Escola Demonstração");
                school.setBairro("Centro");
                school.setCidade(location.city());
                school.setUf(location.state());
            }
            schools.save(school);
            LocalDate registrationBase = original ? null : yesterday;
            createUser(school, schoolNumber, Role.PEDAGOGICAL_COORDINATOR, "coordenador", 1, hash, registrationBase);
            for (int i = 1; i <= 10; i++) {
                createUser(school, schoolNumber, Role.TEACHER, "professor", i, hash, registrationBase);
            }
            int students = original ? 100 : 378;
            for (int i = 1; i <= students; i++) {
                createUser(school, schoolNumber, Role.STUDENT, "aluno", i, hash, registrationBase);
            }
        }
        users.flush();
    }

    private void createUser(School school, int schoolNumber, Role role, String label, int number, String hash,
            LocalDate registrationBase) {
        var user = new SchoolUser();
        String identifier = schoolNumber + "-" + label + "-" + number;
        user.setFullName("Demo " + label + " " + schoolNumber + "-" + number);
        user.setRa("DEMO-" + identifier);
        user.setEmail(identifier + "@example.invalid");
        user.setRole(role);
        user.setSchool(school);
        user.setPasswordHash(hash);
        if (registrationBase != null) {
            int daysBack = (schoolNumber * 7 + number - 1) % 90;
            user.setCreatedAt(registrationBase.minusDays(daysBack).atTime(12, 0).atZone(SEED_ZONE).toInstant());
        }
        // Demo accounts must accept the real terms at login; no consent is fabricated.
        user.setTermsAccepted(false);
        users.save(user);
    }
}
