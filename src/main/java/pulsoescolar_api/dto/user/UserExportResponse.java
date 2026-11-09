package pulsoescolar_api.dto.user;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import pulsoescolar_api.entity.user.Role;

public record UserExportResponse(Instant exportedAt, PersonalData user, LocalDate birthDate, byte[] profilePhoto,
        boolean termsAccepted, List<String> termsAcceptedVersions, List<AcceptedTerm> acceptedTerms) {
    public record PersonalData(Long id, String fullName, String ra, String email, Role role) {}
    public record AcceptedTerm(String title, String version) {}
}
