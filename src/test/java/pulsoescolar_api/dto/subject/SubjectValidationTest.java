package pulsoescolar_api.dto.subject;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.assertThat;

class SubjectValidationTest {
    static ValidatorFactory factory;
    static Validator validator;

    @BeforeAll static void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll static void close() { factory.close(); }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "123", "!@#", "123 😀"})
    void temaETitulo_semLetra_deveRejeitar(String value) {
        assertThat(validator.validate(new ThemeRequest(value))).isNotEmpty();
        var subject = new CreateSubjectRequest("Disciplina", "", List.of(new CreateSubjectRequest.Topic(value, "Descrição", "")));
        assertThat(validator.validate(subject)).anyMatch(v -> v.getPropertyPath().toString().endsWith("title"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Á", "Frações 123!", "中文", "😀 Álgebra", "  \u00a0Geometria\u00a0  "})
    void temaETitulo_comLetraUnicode_deveAceitar(String value) {
        assertThat(validator.validate(new ThemeRequest(value))).isEmpty();
        assertThat(validator.validate(new CreateSubjectRequest.Topic(value, "Descrição", ""))).isEmpty();
    }

    @Test
    void temaETitulo_comQuarentaCaracteresAposTrim_deveAceitar() {
        String name = "  " + "Á".repeat(40) + "  ";
        assertThat(validator.validate(new ThemeRequest(name))).isEmpty();
        assertThat(validator.validate(new CreateSubjectRequest.Topic(name, "Descrição", ""))).isEmpty();
        assertThat(new ThemeRequest(name).name()).hasSize(40);
    }

    @Test
    void temaETitulo_comQuarentaEUmCaracteres_deveRejeitar() {
        String name = "Á".repeat(41);
        assertThat(validator.validate(new ThemeRequest(name))).isNotEmpty();
        assertThat(validator.validate(new CreateSubjectRequest.Topic(name, "Descrição", ""))).isNotEmpty();
    }

    @Test
    void ementaEReferencias_comTextoVazio_deveAceitar() {
        var subject = new CreateSubjectRequest(" Disciplina ", "  ",
                List.of(new CreateSubjectRequest.Topic(" Tema ", " Descrição ", "  ")));
        assertThat(validator.validate(subject)).isEmpty();
        assertThat(subject.name()).isEqualTo("Disciplina");
        assertThat(subject.syllabus()).isEmpty();
        assertThat(subject.topics().getFirst()).isEqualTo(new CreateSubjectRequest.Topic("Tema", "Descrição", ""));
    }
}
