package pulsoescolar_api.exception.invitation;

public class InvitationUnavailableException extends RuntimeException {
    public InvitationUnavailableException() {
        super("Este convite expirou ou já foi utilizado.");
    }
}
