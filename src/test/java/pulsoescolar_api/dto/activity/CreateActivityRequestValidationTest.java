package pulsoescolar_api.dto.activity;

import jakarta.validation.Validation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CreateActivityRequestValidationTest {
    @Test
    void encerramentoDeveSerObrigatorioEFuturo() {
        var now = Instant.parse("2026-11-08T12:00:00Z");
        try (var factory = Validation.byDefaultProvider().configure()
                .clockProvider(() -> Clock.fixed(now, ZoneOffset.UTC)).buildValidatorFactory()) {
            var validator = factory.getValidator();
            for (Instant deadline : new Instant[] {null, now.minusSeconds(1), now}) {
                assertThat(validator.validateProperty(new CreateActivityRequest(deadline, null), "closesAt"))
                        .isNotEmpty();
            }
            assertThat(validator.validateProperty(new CreateActivityRequest(now.plusSeconds(1), null), "closesAt"))
                    .isEmpty();
        }
    }
}
