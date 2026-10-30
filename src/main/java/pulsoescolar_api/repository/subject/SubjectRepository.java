package pulsoescolar_api.repository.subject;
import org.springframework.data.jpa.repository.JpaRepository;
import pulsoescolar_api.entity.subject.Subject;
import java.util.List;
public interface SubjectRepository extends JpaRepository<Subject,Long> {
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select c from Subject c where c.id = :id")
 java.util.Optional<Subject> findLockedById(Long id);
 boolean existsByClassroomIdAndTeacherId(Long classroomId, Long teacherId);
 List<Subject> findByClassroomId(Long classroomId);
}
