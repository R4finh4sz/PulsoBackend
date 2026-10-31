package pulsoescolar_api.repository.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pulsoescolar_api.entity.user.SchoolInvitation;

public interface SchoolInvitationRepository extends JpaRepository<SchoolInvitation, Long> {
    Optional<SchoolInvitation> findBySchoolIdAndRecipientId(Long schoolId, Long recipientId);
    List<SchoolInvitation> findByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientId);
}
