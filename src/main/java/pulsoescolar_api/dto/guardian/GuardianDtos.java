package pulsoescolar_api.dto.guardian;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import pulsoescolar_api.entity.guardian.*;

public final class GuardianDtos {
    private GuardianDtos() {}

    public record VerifyGuardianRequest(@NotBlank @Size(max = 200) String method, @Size(max = 2000) String justification) {}

    public record RequestGuardianCorrectionRequest(@NotBlank @Size(max = 2000) String reason) {}

    public record CorrectGuardianRequest(@NotBlank @Size(max = 100) String name,
                                         @NotBlank @Email @Size(max = 254) String email) {
        @Override public String toString() { return "CorrectGuardianRequest[REDACTED]"; }
    }

    public record GuardianDecisionRequest(@NotNull GuardianDecision decision, GuardianRelationship relationship,
                                          Boolean declaration, @Size(max = 20) String termsVersion,
                                          Boolean privacyAccepted, @Size(max = 20) String privacyVersion) {}

    public record GuardianConfirmationResponse(Long id, Long registrationId, String studentName, LocalDate studentBirthDate,
            String studentRa, Long schoolId, String schoolName, String guardianName, String guardianEmail,
            GuardianStatus status, String correctionReason, int correctionCount, int resendCount, Long checkedById,
            Instant checkedAt, String checkMethod, Instant invitationExpiresAt, GuardianDecision lastDecision,
            Instant createdAt) {
        public static GuardianConfirmationResponse from(GuardianConfirmation c, GuardianInvitation latest) {
            var registration = c.getRegistration();
            return new GuardianConfirmationResponse(c.getId(), registration.getId(), registration.getFullName(),
                    registration.getBirthDate(), registration.getRa(), registration.getSchool().getId(),
                    registration.getSchool().getNome(), c.getGuardianName(), c.getGuardianEmail(), c.getStatus(),
                    c.getCorrectionReason(), c.getCorrectionCount(), c.getResendCount(),
                    c.getCheckedBy() == null ? null : c.getCheckedBy().getId(), c.getCheckedAt(), c.getCheckMethod(),
                    latest == null ? null : latest.getExpiresAt(), latest == null ? null : latest.getDecision(),
                    c.getCreatedAt());
        }
    }

    public record GuardianPageResponse(List<GuardianConfirmationResponse> content, int page, int size,
            long totalElements, int totalPages) {
        public static GuardianPageResponse from(Page<GuardianConfirmationResponse> page) {
            return new GuardianPageResponse(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }

    /** Public page of the correction link. It never exposes the guardian data the student informed. */
    public record GuardianCorrectionView(String studentName, String schoolName, String reason, Instant expiresAt) {}

    public record GuardianCorrectionReceipt(GuardianStatus status) {}

    public record GuardianTermsView(String version, String title, String content) {}

    /** Minimum data shown to the guardian (RN13): no RA, address, grades or e-mail. */
    public record GuardianInvitationView(String studentName, String schoolName, String purpose, Instant expiresAt,
            GuardianTermsView terms, GuardianTermsView privacy, List<GuardianRelationship> relationships) {}

    public record GuardianDecisionReceipt(GuardianDecision decision, Instant decidedAt) {}
}
