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
    private final pulsoescolar_api.repository.user.UserRepository users;

    @Scheduled(cron = "0 0 * * * *", zone = "America/Sao_Paulo")
    public void processRetention() {
        var now = clock.instant().atZone(AccountDeletionService.RETENTION_ZONE);
        int graduationYear = now.toLocalDate().isBefore(java.time.LocalDate.of(now.getYear(), 1, 31))
                ? now.getYear() - 1 : now.getYear();
        var graduationCutoff = java.time.LocalDate.of(graduationYear, 1, 1)
                .atStartOfDay(AccountDeletionService.RETENTION_ZONE).toInstant().minusNanos(1);
        // Month lengths differ; the service rechecks the exact six-month anniversary.
        for (Long id : users.findRetentionCandidates(now.minusMonths(6).plusDays(3).toInstant(), graduationCutoff)) {
            try {
                service.deleteByRetention(id);
            } catch (RuntimeException ex) {
                log.error("Falha na exclusao por retencao do usuario {}", id, ex);
            }
        }
    }

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
