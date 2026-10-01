package pulsoescolar_api.repository.user;
import org.springframework.data.jpa.repository.JpaRepository;
import pulsoescolar_api.entity.user.SchoolUser;
import java.util.*;
public interface UserRepository extends JpaRepository<SchoolUser,Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<SchoolUser> {
 @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
 @org.springframework.data.jpa.repository.Query("UPDATE SchoolUser u SET u.termsAccepted = false")
 void resetTermsAcceptance();
 Optional<SchoolUser> findByEmail(String email);
 boolean existsByEmailOrRa(String email, String ra);
 boolean existsBySchoolIdAndRoleAndDeletedAtIsNull(Long schoolId, pulsoescolar_api.entity.user.Role role);
 List<SchoolUser> findByRoleAndDeletedAtIsNullAndSchoolIdIn(pulsoescolar_api.entity.user.Role role, Collection<Long> schoolIds);
 List<SchoolUser> findByClassroomId(Long classroomId);
}
