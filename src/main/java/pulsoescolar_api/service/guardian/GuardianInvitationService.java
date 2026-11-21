package pulsoescolar_api.service.guardian;

import java.time.Clock;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.guardian.GuardianDtos.*;
import pulsoescolar_api.entity.guardian.*;
import pulsoescolar_api.entity.legal.LegalDocumentType;
import pulsoescolar_api.entity.user.RegistrationStatus;
import pulsoescolar_api.exception.common.BusinessValidationException;
import pulsoescolar_api.exception.invitation.InvitationUnavailableException;
import pulsoescolar_api.repository.guardian.GuardianInvitationRepository;
import pulsoescolar_api.service.legal.LegalDocumentService;
import pulsoescolar_api.service.user.InvitationTokenService;

@Service
@RequiredArgsConstructor
public class GuardianInvitationService {
    static final String PURPOSE = "Confirmar que você é o responsável legal pelo aluno e permitir o vínculo dele a esta "
            + "escola no Pulso Escolar. Esta confirmação não autoriza outros usos dos dados do aluno.";

    private final GuardianInvitationRepository invitations;
    private final LegalDocumentService legalDocuments;
    private final InvitationTokenService tokens;
    private final Clock clock;
    private final pulsoescolar_api.service.legal.PrivacyPolicyAcceptanceService privacy;

    @Transactional(readOnly = true)
    public GuardianInvitationView view(String token) {
        var invitation = open(token, false);
        var registration = invitation.getConfirmation().getRegistration();
        var term = legalDocuments.current(LegalDocumentType.GUARDIAN_TERM)
                .orElseThrow(InvitationUnavailableException::new);
        var policy = legalDocuments.current(LegalDocumentType.PRIVACY_POLICY).orElseThrow(InvitationUnavailableException::new);
        return new GuardianInvitationView(registration.getFullName(), registration.getSchool().getNome(), PURPOSE,
                invitation.getExpiresAt(),
                new GuardianTermsView(LegalDocumentService.label(term.getVersion()), term.getTitle(), term.getContent()),
                new GuardianTermsView(LegalDocumentService.label(policy.getVersion()), policy.getTitle(), policy.getContent()),
                Arrays.asList(GuardianRelationship.values()));
    }

    @Transactional
    public GuardianDecisionReceipt decide(String token, GuardianDecisionRequest input) {
        var invitation = open(token, true);
        var confirmation = invitation.getConfirmation();
        var now = clock.instant();
        if (input.decision() == GuardianDecision.CONFIRM) {
            if (!Boolean.TRUE.equals(input.declaration()) || input.relationship() == null) {
                throw new BusinessValidationException("Declare sua condição de responsável legal para confirmar.");
            }
            var privacyVersion = privacy.resolveRequired(input.privacyAccepted(), input.privacyVersion());
            var term = legalDocuments.current(LegalDocumentType.GUARDIAN_TERM).orElse(null);
            if (term == null || !LegalDocumentService.label(term.getVersion()).equals(input.termsVersion())) {
                throw new BusinessValidationException("Leia a versão atual do termo do responsável para confirmar.");
            }
            invitation.setRelationship(input.relationship());
            invitation.setPrivacyVersion(privacyVersion);
            invitation.setTermsVersion((long) term.getVersion());
            confirmation.setStatus(GuardianStatus.CONFIRMED);
        } else {

            confirmation.setStatus(GuardianStatus.DECLINED);
        }
        invitation.setDecision(input.decision());
        invitation.setDecidedAt(now);
        confirmation.setUpdatedAt(now);
        return new GuardianDecisionReceipt(input.decision(), now);
    }

    private GuardianInvitation open(String token, boolean lock) {
        if (token == null || token.isBlank() || token.length() > 200) throw new InvitationUnavailableException();
        var hash = tokens.hash(token);
        // A leitura roda em transação somente leitura: o Postgres não aceita SELECT ... FOR UPDATE nela.
        var invitation = (lock ? invitations.findForDecision(hash) : invitations.findForView(hash))
                .orElseThrow(InvitationUnavailableException::new);
        var confirmation = invitation.getConfirmation();
        if (!invitation.isOpen(clock.instant()) || confirmation.getStatus() != GuardianStatus.AWAITING_GUARDIAN
                || confirmation.getRegistration().getStatus() != RegistrationStatus.PENDING) {
            throw new InvitationUnavailableException();
        }
        return invitation;
    }
}
