package pulsoescolar_api.repository.schoolcourse;
import org.springframework.data.jpa.repository.JpaRepository;
import pulsoescolar_api.entity.schoolcourse.SchoolCourse;
import java.util.List;
public interface SchoolCourseRepository extends JpaRepository<SchoolCourse,Long> {
 boolean existsByClassroomIdAndTeacherId(Long classroomId, Long teacherId);
 List<SchoolCourse> findByClassroomId(Long classroomId);
}
