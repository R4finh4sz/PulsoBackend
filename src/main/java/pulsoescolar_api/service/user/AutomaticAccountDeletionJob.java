package pulsoescolar_api.service.user;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pulsoescolar_api.repository.user.AccountDeletionRepository;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class AutomaticAccountDeletionJob {
    private final AccountDeletionRepository requests;
    private final AccountDeletionService service;
    private final Clock clock;

    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    public void process() {
        for (Long id : requests.findDueIds(clock.instant().minus(15, ChronoUnit.DAYS))) {
            try {
                service.approveExpired(id);
            } catch (RuntimeException ex) {
                log.error("Falha ao processar exclusão automática do pedido {}", id, ex);
            }
        }
    }
}
