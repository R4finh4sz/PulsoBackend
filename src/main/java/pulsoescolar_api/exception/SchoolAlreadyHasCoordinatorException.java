package pulsoescolar_api.exception;

public class SchoolAlreadyHasCoordinatorException extends BusinessConflictException {
    public SchoolAlreadyHasCoordinatorException() {
        super("Esta escola já possui um coordenador vinculado.");
    }
}
