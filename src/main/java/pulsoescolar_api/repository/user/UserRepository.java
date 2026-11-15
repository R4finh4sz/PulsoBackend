package pulsoescolar_api.repository.user;
import org.springframework.data.jpa.repository.JpaRepository;
import pulsoescolar_api.entity.user.SchoolUser;
import java.util.*;
public interface UserRepository extends JpaRepository<SchoolUser,Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<SchoolUser> {
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select u from SchoolUser u where u.id = :id")
 Optional<SchoolUser> lockById(Long id);

 @org.springframework.data.jpa.repository.Query("""
     select u.id from SchoolUser u where u.deletedAt is null and u.role <> pulsoescolar_api.entity.user.Role.ADMIN
     and (coalesce(u.lastAuthenticatedAt, u.createdAt) <= :cutoff or exists
         (select d.id from ClassroomDeliberation d join d.entries e
          where e.student = u and d.action = pulsoescolar_api.entity.classroom.DeliberationAction.GRADUATE
          and d.performedAt <= :graduationCutoff))
     """)
 List<Long> findRetentionCandidates(java.time.Instant cutoff, java.time.Instant graduationCutoff);
 long countByRaStartingWith(String prefix);
 @Override
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"school", "activeSchool"})
 Optional<SchoolUser> findById(Long id);
 @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
 @org.springframework.data.jpa.repository.Query("UPDATE SchoolUser u SET u.termsAccepted = false")
 void resetTermsAcceptance();
 @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
 @org.springframework.data.jpa.repository.Query("UPDATE SchoolUser u SET u.termsAccepted = false WHERE u.role = :role")
 void resetTermsAcceptanceForRole(pulsoescolar_api.entity.user.Role role);
 @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
 @org.springframework.data.jpa.repository.Query("UPDATE SchoolUser u SET u.termsAccepted = false WHERE u.role <> pulsoescolar_api.entity.user.Role.STUDENT")
 void resetGeneralTermsAcceptance();
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"school", "activeSchool"})
 Optional<SchoolUser> findByEmail(String email);
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"school", "activeSchool"})
 Optional<SchoolUser> findByRa(String ra);
 @org.springframework.data.jpa.repository.Query("select count(u) > 0 from SchoolUser u where lower(u.email) = lower(:email) or u.ra = :ra")
 boolean existsByEmailOrRa(String email, String ra);
 boolean existsBySchoolIdAndRoleAndDeletedAtIsNull(Long schoolId, pulsoescolar_api.entity.user.Role role);
 List<SchoolUser> findByRoleAndDeletedAtIsNullAndSchoolIdIn(pulsoescolar_api.entity.user.Role role, Collection<Long> schoolIds);
 List<SchoolUser> findByClassroomIdAndRoleAndDeletedAtIsNull(Long classroomId, pulsoescolar_api.entity.user.Role role);
}
