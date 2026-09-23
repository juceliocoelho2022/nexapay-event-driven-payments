package br.com.nexapay.payment.api;

import br.com.nexapay.payment.service.RecurringPixScheduleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Validated
@RestController
@RequestMapping("/api/v1/payments/pix/recurring")
public class RecurringPixController {

    private final RecurringPixScheduleService recurringPixScheduleService;

    public RecurringPixController(RecurringPixScheduleService recurringPixScheduleService) {
        this.recurringPixScheduleService = recurringPixScheduleService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYMENT_CREATE')")
    public ResponseEntity<RecurringPixScheduleResponse> create(
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid @RequestBody CreateRecurringPixRequest request) {

        RecurringPixScheduleResponse response =
                recurringPixScheduleService.create(idempotencyKey, request);

        return ResponseEntity
                .created(URI.create("/api/v1/payments/pix/recurring/" + response.id()))
                .body(response);
    }
}
