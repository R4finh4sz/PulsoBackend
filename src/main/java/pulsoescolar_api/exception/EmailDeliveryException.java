package pulsoescolar_api.exception;
public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException(String message) {
        super(message);
    }
    public EmailDeliveryException() {
        super("Não foi possível enviar o e-mail de acesso. O cadastro não foi concluído. Tente novamente.");
    }
}
