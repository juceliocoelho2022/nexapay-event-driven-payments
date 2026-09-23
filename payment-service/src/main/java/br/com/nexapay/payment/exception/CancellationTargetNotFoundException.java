package br.com.nexapay.payment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class CancellationTargetNotFoundException extends RuntimeException {

    public CancellationTargetNotFoundException(String targetType, UUID id) {
        super(targetType + " não encontrado para cancelamento: " + id);
    }
}
