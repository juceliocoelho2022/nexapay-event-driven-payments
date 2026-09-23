package br.com.nexapay.payment.scheduling;

import br.com.nexapay.payment.service.RecurringPixScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class RecurringPixMaterializer {

    private static final Logger log = LoggerFactory.getLogger(RecurringPixMaterializer.class);
    private static final int BATCH_SIZE = 100;

    private final RecurringPixScheduleService service;

    public RecurringPixMaterializer(RecurringPixScheduleService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${nexapay.payment.recurring.fixed-delay-ms:5000}")
    public void materializeDueOccurrences() {
        OffsetDateTime now = OffsetDateTime.now();

        for (UUID scheduleId : service.findDueScheduleIds(now, BATCH_SIZE)) {
            try {
                service.materializeNextOccurrence(scheduleId, OffsetDateTime.now());
            } catch (RuntimeException exception) {
                log.error("Failed to materialize recurring PIX scheduleId={}", scheduleId, exception);
            }
        }
    }
}
