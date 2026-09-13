package pulsoescolar_api.config;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import pulsoescolar_api.repository.user.UserRepository;
import java.util.Locale;
import static org.springframework.security.config.Customizer.withDefaults;
@Configuration
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
 @Bean UserDetailsService userDetailsService(UserRepository repository) {
  return email -> repository.findByEmail(email.strip().toLowerCase(Locale.ROOT))
   .map(u->User.withUsername(u.getEmail()).password(u.getPasswordHash()).roles(u.getRole().name()).build())
   .orElseThrow(()->new UsernameNotFoundException("Credenciais inválidas."));
 }
 @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
  return http.sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a->a.anyRequest().authenticated()).httpBasic(withDefaults()).build();
 }
}
