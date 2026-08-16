package br.com.nexapay.auth.api;

import java.util.List;
import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UUID userId,
        String email,
        List<String> roles
) {
}
