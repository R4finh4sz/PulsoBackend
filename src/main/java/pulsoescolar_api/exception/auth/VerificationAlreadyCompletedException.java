package pulsoescolar_api.exception.auth;

import pulsoescolar_api.exception.common.BusinessConflictException;

public class VerificationAlreadyCompletedException extends BusinessConflictException {
    public VerificationAlreadyCompletedException() {
        super("Código já confirmado.");
    }
}
