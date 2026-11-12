package pulsoescolar_api.dto.subject;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.*;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@NotBlank
@Size(max = 40)
@Pattern(regexp = "(?s).*\\p{L}.*", message = "Informe pelo menos uma letra.")
public @interface ThemeName {
    String message() default "O tema deve ter de 1 a 40 caracteres e conter uma letra.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
