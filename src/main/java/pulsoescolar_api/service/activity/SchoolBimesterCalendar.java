package pulsoescolar_api.service.activity;

import java.time.Instant;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Component;
import pulsoescolar_api.exception.common.BusinessValidationException;

@Component
public class SchoolBimesterCalendar {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final List<MonthDay> STARTS = List.of(MonthDay.of(2, 1), MonthDay.of(4, 1),
            MonthDay.of(7, 1), MonthDay.of(10, 1));
    private static final List<MonthDay> ENDS = List.of(MonthDay.of(4, 30), MonthDay.of(7, 30),
            MonthDay.of(10, 30), MonthDay.of(12, 30));

    public record Period(Instant start, Instant endExclusive) {}

    public Period period(int year, int bimester) {
        if (year < 1 || year > 9999 || bimester < 1 || bimester > 4) {
            throw new BusinessValidationException("Informe um ano de 1 a 9999 e um bimestre de 1 a 4.");
        }
        return new Period(STARTS.get(bimester - 1).atYear(year).atStartOfDay(ZONE).toInstant(),
                ENDS.get(bimester - 1).atYear(year).plusDays(1).atStartOfDay(ZONE).toInstant());
    }
}
