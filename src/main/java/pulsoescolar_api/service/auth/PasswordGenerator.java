package pulsoescolar_api.service.auth;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;
@Component
public class PasswordGenerator {
    private final SecureRandom random = new SecureRandom();
    public String generate() {
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
