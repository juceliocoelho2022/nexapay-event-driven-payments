package br.com.nexapay.account.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotBlank(message = "accountNumber is required")
        @Size(max = 30, message = "accountNumber must have at most 30 characters")
        String accountNumber,

        @NotBlank(message = "holderName is required")
        @Size(max = 120, message = "holderName must have at most 120 characters")
        String holderName
) {
}
