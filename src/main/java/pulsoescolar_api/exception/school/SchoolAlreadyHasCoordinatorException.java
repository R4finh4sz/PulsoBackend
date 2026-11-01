package pulsoescolar_api.exception.school;

import pulsoescolar_api.exception.common.BusinessConflictException;

public class SchoolAlreadyHasCoordinatorException extends BusinessConflictException {
    public SchoolAlreadyHasCoordinatorException() {
        super("Esta escola já possui um coordenador vinculado.");
    }
}
