package pulsoescolar_api.config;

import com.resend.Resend;
import com.resend.services.emails.Emails;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ResendProperties.class)
public class ResendConfig {
    @Bean
    Emails resendEmails(ResendProperties properties) {
        return new Resend(properties.apiKey()).emails();
    }
}
