package pulsoescolar_api.exception.user;

import pulsoescolar_api.exception.common.BusinessValidationException;

public class InvalidRegistrationPhotoException extends BusinessValidationException {
    public InvalidRegistrationPhotoException() {
        super("Envie uma foto JPEG ou PNG válida, com até 4 milhões de pixels e 2 MB.");
    }
}
