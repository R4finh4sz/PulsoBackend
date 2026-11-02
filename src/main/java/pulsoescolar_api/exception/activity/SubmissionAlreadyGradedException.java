package pulsoescolar_api.exception.activity;

import pulsoescolar_api.exception.common.BusinessConflictException;

public class SubmissionAlreadyGradedException extends BusinessConflictException {
    public SubmissionAlreadyGradedException() { super("Esta entrega já foi corrigida."); }
}
