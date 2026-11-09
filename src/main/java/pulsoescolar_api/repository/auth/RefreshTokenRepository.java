package pulsoescolar_api.repository.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import pulsoescolar_api.entity.auth.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"session", "session.user"})
    java.util.Optional<RefreshToken> findById(String hash);
}
