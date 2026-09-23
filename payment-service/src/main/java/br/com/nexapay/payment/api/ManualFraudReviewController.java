package br.com.nexapay.payment.api;

import br.com.nexapay.payment.service.ManualFraudReviewService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments/{paymentId}/fraud-review")
public class ManualFraudReviewController {

    private final ManualFraudReviewService service;

    public ManualFraudReviewController(ManualFraudReviewService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FRAUD_REVIEW')")
    public ManualFraudReviewResponse review(
            @PathVariable UUID paymentId,
            @Valid @RequestBody ManualFraudReviewRequest request,
            Authentication authentication) {

        return service.review(
                paymentId,
                request.decision(),
                request.reason(),
                authentication.getName()
        );
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    public List<ManualFraudReviewAuditResponse> history(
            @PathVariable UUID paymentId) {
        return service.history(paymentId);
    }
}
