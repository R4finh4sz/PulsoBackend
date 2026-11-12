package pulsoescolar_api.exception.terms;

import pulsoescolar_api.exception.common.BusinessConflictException;

public class TermsVersionChangedException extends BusinessConflictException {
    public TermsVersionChangedException() {
        super("Os termos foram atualizados. Consulte a versão atual.");
    }
}
