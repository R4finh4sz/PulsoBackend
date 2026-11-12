package pulsoescolar_api.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {
    public static final String ISSUER = "pulsoescolar-api";
    public static final String AUDIENCE = "pulsoescolar-client";

    @Bean Clock authClock() { return Clock.systemUTC(); }

    @Bean SecretKey jwtSigningKey(JwtProperties properties) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(properties.jwtSecret() == null ? "" : properties.jwtSecret());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("JWT_SECRET deve estar em Base64.");
        }
        if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET deve conter pelo menos 32 bytes aleatórios em Base64.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean JwtDecoder jwtDecoder(SecretKey key, Clock clock) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        var claimConverter = MappedJwtClaimSetConverter.withDefaults(Map.of());
        decoder.setClaimSetConverter(raw -> {
            var claims = claimConverter.convert(raw);
            if (!raw.containsKey("iat")) claims.remove("iat");
            return claims;
        });
        var timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps,
                new JwtIssuerValidator(ISSUER),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(AUDIENCE)),
                new JwtClaimValidator<Object>("exp", value -> value != null),
                new JwtClaimValidator<Object>("iat", value -> value != null)));
        return decoder;
    }
}
