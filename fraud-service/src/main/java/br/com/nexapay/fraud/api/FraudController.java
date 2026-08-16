package br.com.nexapay.fraud.api;

import br.com.nexapay.fraud.domain.FraudDecision;
import br.com.nexapay.fraud.service.FraudService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fraud")
public class FraudController {

    private final FraudService fraudService;

    public FraudController(FraudService fraudService) {
        this.fraudService = fraudService;
    }

    @GetMapping("/payments/{paymentId}")
    public FraudDecisionResponse getByPaymentId(@PathVariable UUID paymentId) {
        FraudDecision decision = fraudService.findByPaymentId(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Fraud decision not found"
                ));

        return FraudDecisionResponse.from(decision);
    }
}
