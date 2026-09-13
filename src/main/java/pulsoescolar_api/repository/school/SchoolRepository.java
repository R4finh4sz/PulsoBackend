package pulsoescolar_api.repository.school;

import org.springframework.data.jpa.repository.JpaRepository;
import pulsoescolar_api.entity.school.School;

public interface SchoolRepository extends JpaRepository<School, Long> {}
