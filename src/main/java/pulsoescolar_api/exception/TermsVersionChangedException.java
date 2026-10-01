package pulsoescolar_api.exception;

public class TermsVersionChangedException extends BusinessConflictException {
    public TermsVersionChangedException() {
        super("Os termos foram atualizados. Consulte a versão atual.");
    }
}
