package pulsoescolar_api;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pulsoescolar_api.exception.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiExceptionHandlerTests {
    static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(new SchoolAlreadyHasCoordinatorException(), 409),
                Arguments.of(new TermsVersionChangedException(), 409),
                Arguments.of(new VerificationAlreadyCompletedException(), 409),
                Arguments.of(new InvalidVerificationCodeException(), 400),
                Arguments.of(new VerificationCodeUnavailableException(), 400),
                Arguments.of(new InvalidRegistrationPhotoException(), 400),
                Arguments.of(new RegistrationPhotoTooLargeException(), 413),
                Arguments.of(new InvitationUnavailableException(), 410),
                Arguments.of(new SessionUnavailableException(), 401),
                Arguments.of(new VerificationCooldownException("Aguarde 3 minutos entre os envios."), 429),
                Arguments.of(new OperationNotAllowedException("Cadastro exige convite."), 403),
                Arguments.of(new BusinessValidationException("Dados inválidos."), 400),
                Arguments.of(new BusinessConflictException("Solicitação já analisada."), 409),
                Arguments.of(new ResourceNotFoundException("Escola não encontrada."), 404));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void preservesStatusAndPublicMessageInProblemDetail(RuntimeException error, int expectedStatus) throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailureController(error))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/failure"))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.detail").value(error.getMessage()))
                .andExpect(jsonPath("$.instance").value("/failure"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    // Non-static: this standalone test controller is not a component-scan candidate.
    @RestController
    class FailureController {
        private final RuntimeException error;

        FailureController(RuntimeException error) { this.error = error; }

        @GetMapping("/failure")
        public void fail() { throw error; }
    }
}
