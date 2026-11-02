package pulsoescolar_api.exception.activity;

import pulsoescolar_api.exception.common.BusinessConflictException;

public class ActivityAlreadySubmittedException extends BusinessConflictException {
    public ActivityAlreadySubmittedException() { super("Você já entregou esta atividade."); }
}
