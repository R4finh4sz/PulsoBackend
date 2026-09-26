package pulsoescolar_api.service.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TwoFactorMailDispatcher {
    private static final Logger log = LoggerFactory.getLogger(TwoFactorMailDispatcher.class);
    private final TaskExecutor executor;
    private final TwoFactorMailService mail;

    public TwoFactorMailDispatcher(@Qualifier("twoFactorMailExecutor") TaskExecutor executor,
                                  TwoFactorMailService mail) {
        this.executor = executor;
        this.mail = mail;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(TwoFactorMailRequested event) {
        try {
            executor.execute(() -> {
                try {
                    mail.send(event.email(), event.code());
                } catch (RuntimeException ex) {
                    log.warn("Falha no envio de 2FA em segundo plano: tipo={}", ex.getClass().getSimpleName());
                }
            });
        } catch (RuntimeException ex) {
            log.warn("Falha ao agendar e-mail de 2FA: tipo={}", ex.getClass().getSimpleName());
        }
    }
}
