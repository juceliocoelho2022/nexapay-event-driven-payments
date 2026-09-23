package br.com.nexapay.payment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.CONFLICT)
public class CancellationConflictException extends RuntimeException {

    public CancellationConflictException(String targetType, UUID id) {
        super(targetType + " não pode mais ser cancelado: " + id);
    }
}
