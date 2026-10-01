package pulsoescolar_api.security.user;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import pulsoescolar_api.exception.InvalidPasswordException;
import pulsoescolar_api.exception.PasswordTooLongException;

@Component
public class PasswordPolicy {
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern NUMBER = Pattern.compile("[0-9]");

    public void requireValidNewPassword(String password) {
        if (password == null || password.length() < 8
                || !UPPERCASE.matcher(password).find() || !NUMBER.matcher(password).find()) {
            throw new InvalidPasswordException();
        }
        // BCrypt limits the UTF-8 input size, not the number of characters.
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new PasswordTooLongException();
        }
    }
}
