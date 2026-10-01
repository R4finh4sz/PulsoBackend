package pulsoescolar_api.repository.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import pulsoescolar_api.entity.auth.PasswordRecovery;

public interface PasswordRecoveryRepository extends JpaRepository<PasswordRecovery, Long> {}
