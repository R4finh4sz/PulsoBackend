package pulsoescolar_api.repository.subject;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import pulsoescolar_api.entity.subject.SubjectInvitation;

public interface SubjectInvitationRepository extends JpaRepository<SubjectInvitation, Long> {
    Optional<SubjectInvitation> findBySubjectIdAndRecipientId(Long subjectId, Long recipientId);
    List<SubjectInvitation> findByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from SubjectInvitation i where i.id = :id")
    Optional<SubjectInvitation> findLockedById(Long id);
}
