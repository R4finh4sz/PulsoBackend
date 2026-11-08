package pulsoescolar_api.dto.classroom;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class CreateClassroomRequestValidationTest {
    static ValidatorFactory factory;
    static Validator validator;

    @BeforeAll static void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll static void close() { factory.close(); }

    @ParameterizedTest
    @ValueSource(strings = {"1º ano · Manhã", "1º ano · Tarde", "1º ano · Noite", "1º ano · Integral",
            "2º ano · Manhã", "2º ano · Tarde", "2º ano · Noite", "2º ano · Integral",
            "3º ano · Manhã", "3º ano · Tarde", "3º ano · Noite", "3º ano · Integral"})
    void nomeNoFormato_deveAceitar(String name) {
        assertThat(validator.validate(new CreateClassroomRequest(name, "A"))).isEmpty();
        assertThat(validator.validate(new UpdateClassroomRequest(name, null))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "Turma A", "0º ano · Manhã", "4º ano · Manhã", "1° ano · Manhã",
            "1º ano - Manhã", "1º ano · Manha", "1º ano · manhã", "1º ano · Matutino",
            " 1º ano · Manhã", "1º ano · Manhã ", "1º ano · Manhã\n", "1º ano  · Manhã"})
    void nomeForaDoFormato_deveRejeitar(String name) {
        assertThat(validator.validate(new CreateClassroomRequest(name, "A")))
                .anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        if (name != null) {
            assertThat(validator.validate(new UpdateClassroomRequest(name, null)))
                    .anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }
    }

    @Test
    void editarSemNome_deveAceitar() {
        assertThat(validator.validate(new UpdateClassroomRequest(null, "B"))).isEmpty();
    }
}
