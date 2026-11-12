package pulsoescolar_api.repository.school;

import org.springframework.data.jpa.repository.JpaRepository;
import pulsoescolar_api.entity.school.School;

public interface SchoolRepository extends JpaRepository<School, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<School> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from School s where s.id = :id")
    java.util.Optional<School> findForUpdate(Long id);
}
