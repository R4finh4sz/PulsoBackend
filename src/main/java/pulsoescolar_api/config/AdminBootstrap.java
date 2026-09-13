package pulsoescolar_api.config;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.entity.*;
import pulsoescolar_api.repository.UserRepository;
import java.util.Locale;
@Component @RequiredArgsConstructor
public class AdminBootstrap implements CommandLineRunner {
 private final UserRepository users;
 private final PasswordEncoder encoder;
 private final Environment environment;
 @Override @Transactional public void run(String... args) {
  String email=environment.getProperty("BOOTSTRAP_ADMIN_EMAIL");
  String password=environment.getProperty("BOOTSTRAP_ADMIN_PASSWORD");
  if(email==null || email.isBlank()) return;
  email=email.strip().toLowerCase(Locale.ROOT);
  if(users.findByEmail(email).isPresent()) return;
  if(password==null || password.length()<12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
   throw new IllegalStateException("Bootstrap admin password must have at least 12 characters and at most 72 bytes");
  var admin=new SchoolUser(); admin.setFullName("Administrator"); admin.setRa("SYSTEM-ADMIN");
  admin.setEmail(email); admin.setPasswordHash(encoder.encode(password)); admin.setRole(Role.ADMIN);
  users.save(admin);
 }
}
