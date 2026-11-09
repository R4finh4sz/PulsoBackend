package pulsoescolar_api.repository.faq;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pulsoescolar_api.entity.faq.Faq;
import pulsoescolar_api.entity.user.Role;

public interface FaqRepository extends JpaRepository<Faq, Long> {
    List<Faq> findAllByOrderByIdAsc();
    List<Faq> findByTargetRoleOrderByIdAsc(Role targetRole);
}
