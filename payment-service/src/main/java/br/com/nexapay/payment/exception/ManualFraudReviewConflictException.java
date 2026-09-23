package br.com.nexapay.payment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.CONFLICT)
public class ManualFraudReviewConflictException extends RuntimeException {

    public ManualFraudReviewConflictException(UUID paymentId) {
        super("Pagamento não está mais disponível para revisão manual: " + paymentId);
    }
}
