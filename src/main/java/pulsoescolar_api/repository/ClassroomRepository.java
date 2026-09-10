package pulsoescolar_api.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import pulsoescolar_api.entity.Classroom;
import java.util.List;
public interface ClassroomRepository extends JpaRepository<Classroom,Long> {
 List<Classroom> findByTeachersId(Long teacherId);
}
