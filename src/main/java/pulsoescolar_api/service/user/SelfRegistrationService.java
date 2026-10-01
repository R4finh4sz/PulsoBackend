package pulsoescolar_api.service.user;

import pulsoescolar_api.exception.BusinessConflictException;
import pulsoescolar_api.exception.BusinessValidationException;
import pulsoescolar_api.exception.OperationNotAllowedException;
import pulsoescolar_api.exception.ResourceNotFoundException;
import pulsoescolar_api.exception.TermsVersionChangedException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.user.*;
import pulsoescolar_api.entity.terms.TermsVersion;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.school.SchoolRepository;
import pulsoescolar_api.repository.terms.TermsRepository;
import pulsoescolar_api.repository.user.*;

@Service
@RequiredArgsConstructor
public class SelfRegistrationService {
    private final RegistrationRepository requests;
    private final UserRepository users;
    private final SchoolRepository schools;
    private final TermsRepository terms;
    private final PasswordEncoder passwords;
    private final Validator validator;
    private final Clock clock;

    @Transactional
    public RegistrationReceipt register(SelfRegistrationRequest input, Role role, byte[] photo) {
        if (!validator.validate(input).isEmpty()) {
            throw new BusinessValidationException("Confira os dados obrigatórios do cadastro.");
        }
        if (role != Role.STUDENT) {
            throw new OperationNotAllowedException("Coordenadores e professores só podem se cadastrar por convite.");
        }
        if (input.role() != null && input.role() != role) {
            throw new BusinessValidationException("O perfil deve corresponder à rota de cadastro.");
        }
        if (input.birthDate().isAfter(LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo"))).minusYears(15))) {
            throw new BusinessValidationException("É necessário ter pelo menos 15 anos.");
        }
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessValidationException("A senha deve ter no máximo 72 bytes.");
        }
        String email = input.email().strip().toLowerCase(Locale.ROOT);
        String ra = input.ra().strip();
        if (users.existsByEmailOrRa(email, ra) || requests.existsByEmailOrRa(email, ra)) {
            throw new BusinessConflictException("Já existe uma conta ou solicitação com este e-mail ou RA.");
        }
        var school = schools.findById(input.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Escola não encontrada."));
        // Share the publication lock so an outdated acceptance cannot race a new terms version.
        var term = terms.lockCurrent();
        if (term == null || term.getVersion() == 0) {
            throw new ResourceNotFoundException("Nenhum termo publicado.");
        }
        if (!TermsVersion.label(term.getVersion()).equals(input.termsVersion())) {
            throw new TermsVersionChangedException();
        }
        var request = new RegistrationRequest();
        request.setFullName(input.name().strip());
        request.setBirthDate(input.birthDate());
        request.setRa(ra);
        request.setEmail(email);
        request.setPasswordHash(passwords.encode(input.password()));
        request.setRole(role);
        request.setSchool(school);
        request.setTermsVersion(term.getVersion());
        request.setProfilePhoto(photo);
        request.setStatus(RegistrationStatus.PENDING);
        request.setRequestedAt(clock.instant());
        requests.saveAndFlush(request);
        return new RegistrationReceipt(request.getId(), request.getStatus());
    }
}
