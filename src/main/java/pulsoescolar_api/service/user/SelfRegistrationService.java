package pulsoescolar_api.service.user;

import pulsoescolar_api.exception.common.BusinessConflictException;
import pulsoescolar_api.exception.common.BusinessValidationException;
import pulsoescolar_api.exception.common.OperationNotAllowedException;
import pulsoescolar_api.exception.common.ResourceNotFoundException;
import pulsoescolar_api.exception.terms.TermsVersionChangedException;

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
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.school.SchoolRepository;
import pulsoescolar_api.repository.user.*;

@Service
@RequiredArgsConstructor
public class SelfRegistrationService {
    private final RegistrationRepository requests;
    private final UserRepository users;
    private final SchoolRepository schools;
    private final pulsoescolar_api.service.legal.LegalDocumentService legalDocuments;
    private final PasswordEncoder passwords;
    private final Validator validator;
    private final pulsoescolar_api.repository.terms.TermsRepository generalTerms;
    private final Clock clock;
    private final pulsoescolar_api.security.user.PasswordPolicy passwordPolicy;
    private final pulsoescolar_api.service.guardian.GuardianConfirmationService guardians;
    private final pulsoescolar_api.service.legal.PrivacyPolicyAcceptanceService privacy;

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
        if (input.birthDate().isBefore(LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo"))).minusYears(90))) {
            throw new BusinessValidationException("A idade máxima permitida é de 90 anos.");
        }
        passwordPolicy.requireValidNewPassword(input.password());
        if (!input.password().equals(input.confirmPassword())) {
            throw new BusinessValidationException("A confirmação de senha deve ser igual à senha.");
        }
        String email = input.email().strip().toLowerCase(Locale.ROOT);
        String ra = input.ra().strip();
        guardians.requireValidContactIfMinor(input.birthDate(), email, input.guardianName(), input.guardianEmail());
        if (users.existsByEmailOrRa(email, ra)) {
            throw new BusinessConflictException("Já existe uma conta com este e-mail ou RA. Entre na sua conta ou recupere sua senha.");
        }
        if (requests.existsByEmailOrRa(email, ra)) {
            throw new BusinessConflictException("Já existe uma solicitação de cadastro com este e-mail ou RA.");
        }
        var school = schools.findById(input.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Escola não encontrada."));
        school.requireActive();
        var request = new RegistrationRequest();
        if (guardians.requiresGuardian(input.birthDate())) {
            var term = legalDocuments.lockCurrentForAcceptance(pulsoescolar_api.entity.legal.LegalDocumentType.STUDENT_TERM);
            if (!pulsoescolar_api.service.legal.LegalDocumentService.label(term.getVersion()).equals(input.termsVersion())) {
                throw new TermsVersionChangedException();
            }
            request.setStudentTermVersion(term.getVersion());
        } else {
            // Maior de 18 anos: termos gerais, sem responsável legal.
            var general = generalTerms.lockCurrent();
            if (general == null || general.getVersion() == 0) {
                throw new ResourceNotFoundException("Nenhum termo publicado.");
            }
            if (!pulsoescolar_api.entity.terms.TermsVersion.label(general.getVersion()).equals(input.termsVersion())) {
                throw new TermsVersionChangedException();
            }
            request.setTermsVersion(general.getVersion());
        }
        Integer privacyVersion = privacy.resolveRequired(input.privacyAccepted(), input.privacyVersion());
        request.setPrivacyVersion(privacyVersion);
        request.setFullName(input.name().strip());
        request.setBirthDate(input.birthDate());
        request.setRa(ra);
        request.setEmail(email);
        request.setPasswordHash(passwords.encode(input.password()));
        request.setRole(role);
        request.setSchool(school);
        request.setProfilePhoto(photo);
        request.setStatus(RegistrationStatus.PENDING);
        request.setRequestedAt(clock.instant());
        requests.saveAndFlush(request);
        guardians.openIfRequired(request, input.guardianName(), input.guardianEmail());
        return new RegistrationReceipt(request.getId(), request.getStatus());
    }
}
