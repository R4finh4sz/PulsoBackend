package pulsoescolar_api.config;
import java.util.Locale;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.security.auth.SessionJwtAuthenticationConverter;
@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService userDetailsService(UserRepository repository) {
        return email -> repository.findByEmail(email.strip().toLowerCase(Locale.ROOT))
                .map(u -> User.withUsername(u.getEmail()).password(u.getPasswordHash())
                        .roles(u.getRole().name()).build())
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas."));
    }
    @Bean AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,
            SessionJwtAuthenticationConverter converter) throws Exception {
        return http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(s -> s.securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .csrf(c -> c.disable())
                .requestCache(c -> c.disable())
                .httpBasic(c -> c.disable())
                .formLogin(c -> c.disable())
                .logout(c -> c.disable())
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/auth/2fa/verify", "/api/auth/2fa/resend").hasAuthority("TWO_FACTOR_PENDING")
                        .requestMatchers(HttpMethod.PATCH, "/api/auth/password").hasAnyAuthority("PASSWORD_CHANGE_REQUIRED", "ROLE_ADMIN", "ROLE_PEDAGOGICAL_COORDINATOR", "ROLE_TEACHER", "ROLE_STUDENT")
                        .requestMatchers(HttpMethod.POST, "/api/terms").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/terms").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/terms").hasAnyAuthority("PASSWORD_CHANGE_REQUIRED", "ROLE_ADMIN", "ROLE_PEDAGOGICAL_COORDINATOR", "ROLE_TEACHER", "ROLE_STUDENT")
                        .requestMatchers(HttpMethod.POST, "/api/terms/accept").hasAnyAuthority("PASSWORD_CHANGE_REQUIRED", "ROLE_ADMIN", "ROLE_PEDAGOGICAL_COORDINATOR", "ROLE_TEACHER", "ROLE_STUDENT")
                        .anyRequest().hasAnyRole("ADMIN", "PEDAGOGICAL_COORDINATOR", "TEACHER", "STUDENT"))
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)))
                .build();
    }
}
