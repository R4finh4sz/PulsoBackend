package pulsoescolar_api.config;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.entity.user.SchoolUser;
import pulsoescolar_api.repository.user.UserRepository;
import java.util.Locale;
@Component @RequiredArgsConstructor
public class AdminBootstrap implements CommandLineRunner {
 private final UserRepository users;
 private final PasswordEncoder encoder;
 private final Environment environment;
 @Override @Transactional public void run(String... args) {
  String email=environment.getProperty("BOOTSTRAP_ADMIN_EMAIL");
  String password=environment.getProperty("BOOTSTRAP_ADMIN_PASSWORD");
  if(email==null || email.isBlank()) {
   throw new IllegalStateException("Configure BOOTSTRAP_ADMIN_EMAIL e BOOTSTRAP_ADMIN_PASSWORD para iniciar a aplicação.");
  }
  if(password==null || password.length()<12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
   throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD deve ter pelo menos 12 caracteres e no máximo 72 bytes.");
  email=email.strip().toLowerCase(Locale.ROOT);
  var existing = users.findByEmail(email);
  if(existing.isPresent()) {
   if (existing.get().getRole() != Role.ADMIN || existing.get().getDeletedAt() != null)
    throw new IllegalStateException("BOOTSTRAP_ADMIN_EMAIL deve identificar um administrador ativo.");
   return;
  }
  var admin=new SchoolUser(); admin.setFullName("Administrator"); admin.setRa("SYSTEM-ADMIN");
  admin.setEmail(email); admin.setPasswordHash(encoder.encode(password)); admin.setRole(Role.ADMIN);
  admin.setTwoFactorEnabled(true);
  users.save(admin);
 }
}
