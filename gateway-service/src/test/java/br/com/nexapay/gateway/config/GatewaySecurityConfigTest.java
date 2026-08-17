package br.com.nexapay.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GatewaySecurityConfigTest {

    @Test
    void shouldMapPermissionsClaimWithoutScopePrefix() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject("user-123")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("permissions", List.of("PAYMENT_READ", "ACCOUNT_READ"))
                .build();

        var converter = new GatewaySecurityConfig().jwtAuthenticationConverter();
        var authentication = converter.convert(jwt).block();

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("PAYMENT_READ", "ACCOUNT_READ");
    }
}
