package pulsoescolar_api.service.user;

import lombok.RequiredArgsConstructor;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.user.UserExportResponse;
import pulsoescolar_api.dto.notification.NotificationResponse;
import pulsoescolar_api.repository.user.AccountDeletionRepository;
import pulsoescolar_api.repository.user.RegistrationRepository;
import pulsoescolar_api.repository.notification.UserNotificationRepository;
import pulsoescolar_api.repository.subject.SubjectInvitationRepository;
import pulsoescolar_api.security.CurrentUser;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserExportService {
    private final CurrentUser currentUser;
    private final Clock clock;
    private final UserProfileService profiles;
    private final RegistrationRepository registrations;
    private final AccountDeletionRepository deletions;
    private final UserNotificationRepository notifications;
    private final SubjectInvitationRepository invitations;

    public UserExportResponse export() {
        var user = currentUser.get();
        var profile = profiles.profile();
        return new UserExportResponse(clock.instant(), profile.user(), user.getBirthDate(), user.getProfilePhoto(),
                user.isTermsAccepted(), user.getTermsAcceptedVersions(), profile.schools(),
                profile.classrooms().stream().map(room -> new UserExportResponse.Classroom(
                        room.id(), room.name(), room.identifier())).toList(),
                registrations.findByUserId(user.getId()).map(r -> new UserExportResponse.Registration(
                        r.getId(), r.getFullName(), r.getRa(), r.getEmail(), r.getBirthDate(),
                        r.getSchool().getId(), r.getTermsVersion(), r.getProfilePhoto(), r.getStatus(),
                        r.getRequestedAt(), r.getReviewedAt(), r.getReviewReason())).orElse(null),
                deletions.findByRequesterIdOrderByRequestedAtDesc(user.getId()).stream()
                        .map(r -> new UserExportResponse.Deletion(r.getId(), r.getReason(), r.getStatus(),
                                r.getRequestedAt(), r.getReviewedAt(), r.getReviewReason())).toList(),
                notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(user.getId()).stream()
                        .map(NotificationResponse::from).toList(),
                invitations.findByRecipientIdOrderByCreatedAtDescIdDesc(user.getId()).stream()
                        .map(i -> new UserExportResponse.Invitation(i.getId(), i.getSubject().getId(),
                                i.getStatus(), i.getCreatedAt(), i.getRespondedAt())).toList());
    }
}
