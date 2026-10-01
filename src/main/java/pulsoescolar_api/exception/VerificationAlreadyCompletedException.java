package pulsoescolar_api.exception;

public class VerificationAlreadyCompletedException extends BusinessConflictException {
    public VerificationAlreadyCompletedException() {
        super("Código já confirmado.");
    }
}
