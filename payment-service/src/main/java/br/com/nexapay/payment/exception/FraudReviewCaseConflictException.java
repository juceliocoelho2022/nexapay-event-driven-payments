package br.com.nexapay.payment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.CONFLICT)
public class FraudReviewCaseConflictException extends RuntimeException {

    public FraudReviewCaseConflictException(UUID paymentId, String message) {
        super(message + ": " + paymentId);
    }
}
