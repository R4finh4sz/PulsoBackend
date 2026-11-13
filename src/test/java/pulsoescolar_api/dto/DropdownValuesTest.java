package pulsoescolar_api.dto;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pulsoescolar_api.dto.school.BrazilianState;
import pulsoescolar_api.dto.user.RegistrationRequestType;
import pulsoescolar_api.entity.classroom.*;
import pulsoescolar_api.entity.subject.SubjectInvitationStatus;
import pulsoescolar_api.entity.user.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class DropdownValuesTest {
    private final JsonMapper json = JsonMapper.builder().build();

    static Stream<Class<?>> enumTypes() {
        return Stream.of(BrazilianState.class, Role.class, RegistrationStatus.class, DeletionStatus.class,
                SchoolInvitationStatus.class, SubjectInvitationStatus.class, DeliberationAction.class,
                DeliberationResult.class, RegistrationRequestType.class);
    }

    @ParameterizedTest @MethodSource("enumTypes")
    void acceptsEveryDeclaredOptionAndKeepsResponseFormat(Class<?> type) {
        for (var value : type.getEnumConstants()) {
            String encoded = json.writeValueAsString(((Enum<?>) value).name());
            assertThat(json.readValue(encoded, type)).isEqualTo(value);
            assertThat(json.writeValueAsString(value)).isEqualTo(encoded);
        }
    }

    @ParameterizedTest @MethodSource("enumTypes")
    void rejectsNumbersUnknownNamesCaseChangesAndExtraWhitespace(Class<?> type) {
        String valid = ((Enum<?>) type.getEnumConstants()[0]).name();
        for (String invalid : new String[]{"0", "1", "1.0", "true", "\"0\"", "\"INVALID_OPTION\"", "\"\"",
                json.writeValueAsString(valid.toLowerCase(java.util.Locale.ROOT)), json.writeValueAsString(" " + valid + " ")}) {
            assertThatThrownBy(() -> json.readValue(invalid, type)).as(type.getSimpleName() + ": " + invalid)
                    .isInstanceOf(tools.jackson.core.JacksonException.class);
        }
    }
}
