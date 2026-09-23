package br.com.nexapay.payment.scheduling;

import br.com.nexapay.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class ScheduledPaymentExecutor {

    private static final Logger log = LoggerFactory.getLogger(ScheduledPaymentExecutor.class);
    private static final int BATCH_SIZE = 100;

    private final PaymentService paymentService;

    public ScheduledPaymentExecutor(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Scheduled(
            fixedDelayString = "${nexapay.payment.scheduler.fixed-delay-ms:5000}",
            initialDelayString = "${nexapay.payment.scheduler.initial-delay-ms:0}")
    public void executeDuePayments() {
        OffsetDateTime now = OffsetDateTime.now();

        for (UUID paymentId : paymentService.findDueScheduledPaymentIds(now, BATCH_SIZE)) {
            try {
                paymentService.executeDueScheduledPayment(paymentId, OffsetDateTime.now());
            } catch (RuntimeException exception) {
                log.error("Failed to execute scheduled payment paymentId={}", paymentId, exception);
            }
        }
    }
}
