package br.com.nexapay.payment.api;

import br.com.nexapay.payment.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/pix")
    @PreAuthorize("hasAuthority('PAYMENT_CREATE')")
    public ResponseEntity<PaymentResponse> createPixPayment(
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid @RequestBody CreatePixPaymentRequest request) {

        PaymentResponse response = paymentService.createPixPayment(idempotencyKey, request);

        return ResponseEntity
                .created(URI.create("/api/v1/payments/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    public PaymentResponse findById(@PathVariable UUID id) {
        return paymentService.findById(id);
    }
}
