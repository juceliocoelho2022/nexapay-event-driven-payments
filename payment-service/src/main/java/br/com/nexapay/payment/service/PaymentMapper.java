package br.com.nexapay.payment.service;

import br.com.nexapay.payment.api.PaymentResponse;
import br.com.nexapay.payment.domain.Payment;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getPayerAccountId(),
                payment.getPixKey(),
                payment.getAmount(),
                payment.getDescription(),
                payment.getStatus(),
                payment.getCreatedAt()
        );
    }
}
