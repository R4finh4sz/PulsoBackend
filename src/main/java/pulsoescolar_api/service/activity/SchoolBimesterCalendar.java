package pulsoescolar_api.service.activity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Component;
import pulsoescolar_api.exception.common.BusinessValidationException;

@Component
public class SchoolBimesterCalendar {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final List<LocalDate> STARTS = List.of(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 10, 1));
    private static final List<LocalDate> ENDS = List.of(LocalDate.of(2026, 4, 30), LocalDate.of(2026, 7, 30),
            LocalDate.of(2026, 10, 30), LocalDate.of(2026, 12, 30));

    public record Period(Instant start, Instant endExclusive) {}

    public Period period(int year, int bimester) {
        if (year != 2026 || bimester < 1 || bimester > 4) {
            throw new BusinessValidationException("Informe o ano 2026 e um bimestre de 1 a 4.");
        }
        return new Period(STARTS.get(bimester - 1).atStartOfDay(ZONE).toInstant(),
                ENDS.get(bimester - 1).plusDays(1).atStartOfDay(ZONE).toInstant());
    }
}
