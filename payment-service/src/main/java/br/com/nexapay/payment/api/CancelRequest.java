package br.com.nexapay.payment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelRequest(
        @NotBlank
        @Size(max = 255)
        String reason
) {
}
