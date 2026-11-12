package pulsoescolar_api.service.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationMailDispatcher {
    private static final Logger log = LoggerFactory.getLogger(NotificationMailDispatcher.class);
    private final TaskExecutor executor;
    private final EmailSender sender;

    public NotificationMailDispatcher(@Qualifier("twoFactorMailExecutor") TaskExecutor executor, EmailSender sender) {
        this.executor = executor;
        this.sender = sender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(NotificationMailRequested event) {
        try {
            executor.execute(() -> {
                try {
                    sender.send(event.email(), event.subject(), event.text());
                } catch (RuntimeException ex) {
                    log.warn("Falha no envio de notificação por e-mail: tipo={}", ex.getClass().getSimpleName());
                }
            });
        } catch (RuntimeException ex) {
            log.warn("Falha ao agendar notificação por e-mail: tipo={}", ex.getClass().getSimpleName());
        }
    }
}
