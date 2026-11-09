package pulsoescolar_api.service.activity;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import pulsoescolar_api.exception.common.BusinessValidationException;
import static org.assertj.core.api.Assertions.*;

class SchoolBimesterCalendarTest {
    private final SchoolBimesterCalendar calendar = new SchoolBimesterCalendar();

    @ParameterizedTest
    @ValueSource(ints = {1, 2025, 2026, 2027, 2028, 9999})
    void usesRequestedYearForEveryBimester(int year) {
        var zone = ZoneId.of("America/Sao_Paulo");
        int[] startMonths = {2, 4, 7, 10};
        int[] endMonths = {4, 7, 10, 12};
        for (int bimester = 1; bimester <= 4; bimester++) {
            var period = calendar.period(year, bimester);
            assertThat(period.start()).isEqualTo(LocalDate.of(year, startMonths[bimester - 1], 1)
                    .atStartOfDay(zone).toInstant());
            assertThat(period.endExclusive()).isEqualTo(LocalDate.of(year, endMonths[bimester - 1], 30)
                    .plusDays(1).atStartOfDay(zone).toInstant());
        }
    }

    @ParameterizedTest
    @CsvSource({"0,1", "-1,1", "10000,1", "2027,0", "2027,5"})
    void rejectsInvalidYearOrBimester(int year, int bimester) {
        assertThatThrownBy(() -> calendar.period(year, bimester)).isInstanceOf(BusinessValidationException.class);
    }
}
