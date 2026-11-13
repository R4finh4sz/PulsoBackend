package pulsoescolar_api.exception.common;

public class AddressLookupUnavailableException extends RuntimeException {
    public AddressLookupUnavailableException(Throwable cause) {
        super("Não foi possível verificar o endereço no ViaCEP. Tente novamente.", cause);
    }
}
