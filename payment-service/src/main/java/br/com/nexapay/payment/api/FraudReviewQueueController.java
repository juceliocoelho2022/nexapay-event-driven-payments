package br.com.nexapay.payment.api;

import br.com.nexapay.payment.domain.FraudReviewPriority;
import br.com.nexapay.payment.service.FraudReviewQueueService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fraud-review/cases")
@PreAuthorize("hasAuthority('FRAUD_REVIEW')")
public class FraudReviewQueueController {

    private final FraudReviewQueueService service;

    public FraudReviewQueueController(FraudReviewQueueService service) {
        this.service = service;
    }

    @GetMapping
    public List<FraudReviewCaseResponse> listOpenCases(
            @RequestParam(required = false)
            FraudReviewPriority priority,
            @RequestParam(required = false)
            Boolean overdue,
            @RequestParam(required = false)
            Boolean available) {
        return service.listOpenCases(
                priority,
                overdue,
                available
        );
    }

    @PostMapping("/{paymentId}/claim")
    public FraudReviewCaseResponse claim(
            @PathVariable UUID paymentId,
            Authentication authentication) {
        return service.claim(
                paymentId,
                authentication.getName()
        );
    }

    @PostMapping("/{paymentId}/release")
    public FraudReviewCaseResponse release(
            @PathVariable UUID paymentId,
            Authentication authentication) {
        return service.release(
                paymentId,
                authentication.getName()
        );
    }
}
