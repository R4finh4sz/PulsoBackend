package pulsoescolar_api.exception;

public class RegistrationPhotoTooLargeException extends RuntimeException {
    public RegistrationPhotoTooLargeException() {
        super("A foto deve ter no máximo 2 MB.");
    }
}
