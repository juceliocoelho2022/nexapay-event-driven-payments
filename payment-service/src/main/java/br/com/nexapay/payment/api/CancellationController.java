package br.com.nexapay.payment.api;

import br.com.nexapay.payment.domain.CancellationTargetType;
import br.com.nexapay.payment.service.CancellationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class CancellationController {

    private final CancellationService cancellationService;

    public CancellationController(CancellationService cancellationService) {
        this.cancellationService = cancellationService;
    }

    @PostMapping("/api/v1/payments/{paymentId}/cancel")
    @PreAuthorize("hasAuthority('PAYMENT_CANCEL')")
    public CancellationResponse cancelScheduledPayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody CancelRequest request,
            Authentication authentication) {

        return cancellationService.cancelScheduledPayment(
                paymentId,
                request.reason(),
                authentication.getName()
        );
    }

    @GetMapping("/api/v1/payments/{paymentId}/cancellations")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    public List<CancellationAuditResponse> paymentCancellationHistory(
            @PathVariable UUID paymentId) {
        return cancellationService.findHistory(
                CancellationTargetType.PAYMENT,
                paymentId
        );
    }

    @GetMapping("/api/v1/payments/pix/recurring/{scheduleId}/cancellations")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    public List<CancellationAuditResponse> recurringCancellationHistory(
            @PathVariable UUID scheduleId) {
        return cancellationService.findHistory(
                CancellationTargetType.RECURRING_SCHEDULE,
                scheduleId
        );
    }

    @PostMapping("/api/v1/payments/pix/recurring/{scheduleId}/cancel")
    @PreAuthorize("hasAuthority('PAYMENT_CANCEL')")
    public CancellationResponse cancelRecurringSchedule(
            @PathVariable UUID scheduleId,
            @Valid @RequestBody CancelRequest request,
            Authentication authentication) {

        return cancellationService.cancelRecurringSchedule(
                scheduleId,
                request.reason(),
                authentication.getName()
        );
    }
}
