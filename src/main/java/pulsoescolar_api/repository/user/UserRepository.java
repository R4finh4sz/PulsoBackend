package pulsoescolar_api.repository.user;
import org.springframework.data.jpa.repository.JpaRepository;
import pulsoescolar_api.entity.user.SchoolUser;
import java.util.*;
public interface UserRepository extends JpaRepository<SchoolUser,Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<SchoolUser> {
 long countByRaStartingWith(String prefix);
 @Override
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"school", "activeSchool"})
 Optional<SchoolUser> findById(Long id);
 @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
 @org.springframework.data.jpa.repository.Query("UPDATE SchoolUser u SET u.termsAccepted = false")
 void resetTermsAcceptance();
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
