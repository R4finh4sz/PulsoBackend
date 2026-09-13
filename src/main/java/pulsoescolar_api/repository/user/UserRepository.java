package pulsoescolar_api.repository.user;
import org.springframework.data.jpa.repository.JpaRepository;
import pulsoescolar_api.entity.user.SchoolUser;
import java.util.*;
public interface UserRepository extends JpaRepository<SchoolUser,Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<SchoolUser> {
 Optional<SchoolUser> findByEmail(String email);
 List<SchoolUser> findByClassroomId(Long classroomId);
}
