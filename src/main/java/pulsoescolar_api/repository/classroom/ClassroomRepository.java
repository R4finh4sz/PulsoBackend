package pulsoescolar_api.repository.classroom;
import org.springframework.data.jpa.repository.JpaRepository;
import pulsoescolar_api.entity.classroom.Classroom;
import java.util.List;
public interface ClassroomRepository extends JpaRepository<Classroom,Long> {
 List<Classroom> findByTeachersId(Long teacherId);
 List<Classroom> findBySchoolId(Long schoolId);
}
