package pulsoescolar_api.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import pulsoescolar_api.entity.SchoolUser;
import java.util.*;
public interface UserRepository extends JpaRepository<SchoolUser,Long> {
 Optional<SchoolUser> findByEmail(String email);
 List<SchoolUser> findByClassroomId(Long classroomId);
}
