package pulsoescolar_api.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import pulsoescolar_api.entity.Subject;
import java.util.List;
public interface SubjectRepository extends JpaRepository<Subject,Long> {
 List<Subject> findByClassroomId(Long classroomId);
}
