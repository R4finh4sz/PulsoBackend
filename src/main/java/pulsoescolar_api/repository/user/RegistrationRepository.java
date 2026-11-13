package pulsoescolar_api.repository.user;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import pulsoescolar_api.entity.user.RegistrationRequest;

public interface RegistrationRepository extends JpaRepository<RegistrationRequest, Long>,
        JpaSpecificationExecutor<RegistrationRequest> {
    interface InboxEntry {
        Long getId();
        String getType();
    }

    @Query(value = """
            select r.id, 'REGISTRATION' as type, r.requested_at
            from registration_requests r
            where :includeRegistrations = true and r.status = :status and r.role in (:roles)
              and (cast(:schoolId as bigint) is null or r.school_id = :schoolId)
            union all
            select t.id, 'TRANSFER' as type, t.requested_at
            from student_transfers t join school_users u on u.id = t.student_id
            where :includeTransfers = true and t.status = :status and u.deleted_at is null
              and u.role = 'STUDENT' and u.role in (:roles)
              and (cast(:schoolId as bigint) is null or t.destination_id = :schoolId)
            order by requested_at, type, id
            """, countQuery = """
            select count(*) from (
                select r.id from registration_requests r
                where :includeRegistrations = true and r.status = :status and r.role in (:roles)
                  and (cast(:schoolId as bigint) is null or r.school_id = :schoolId)
                union all
                select t.id from student_transfers t join school_users u on u.id = t.student_id
                where :includeTransfers = true and t.status = :status and u.deleted_at is null
                  and u.role = 'STUDENT' and u.role in (:roles)
                  and (cast(:schoolId as bigint) is null or t.destination_id = :schoolId)
            ) inbox
            """, nativeQuery = true)
    org.springframework.data.domain.Page<InboxEntry> findInbox(String status, java.util.List<String> roles,
            Long schoolId, boolean includeRegistrations, boolean includeTransfers,
            org.springframework.data.domain.Pageable pageable);

    @Query("select count(r) > 0 from RegistrationRequest r where lower(r.email) = lower(:email) or r.ra = :ra")
    boolean existsByEmailOrRa(String email, String ra);
    boolean existsByEmail(String email);
    Optional<RegistrationRequest> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RegistrationRequest r WHERE r.id = :id")
    Optional<RegistrationRequest> findForReview(Long id);
}
