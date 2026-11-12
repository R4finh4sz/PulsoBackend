package pulsoescolar_api.repository.classroom;

import org.springframework.data.jpa.repository.JpaRepository;
import pulsoescolar_api.entity.classroom.ClassroomDeliberation;

public interface ClassroomDeliberationRepository extends JpaRepository<ClassroomDeliberation, Long> {
}
