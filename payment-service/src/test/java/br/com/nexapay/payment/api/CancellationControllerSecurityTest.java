package br.com.nexapay.payment.api;

import br.com.nexapay.payment.config.PaymentSecurityConfig;
import br.com.nexapay.payment.domain.CancellationTargetType;
import br.com.nexapay.payment.service.CancellationService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CancellationController.class)
@Import(PaymentSecurityConfig.class)
class CancellationControllerSecurityTest {

    private static final String SECRET = "nexapay-local-dev-secret-2026-change-me";
    private static final String ISSUER = "https://nexapay.local/auth";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CancellationService cancellationService;

    @Test
    void shouldRejectCancellationWithoutPaymentCancelPermission() throws Exception {
        mockMvc.perform(post("/api/v1/payments/{id}/cancel", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token(List.of("PAYMENT_READ")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Not authorized"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowScheduledPaymentCancellationWithPermission() throws Exception {
        UUID id = UUID.randomUUID();

        when(cancellationService.cancelScheduledPayment(
                eq(id),
                eq("Cliente solicitou"),
                anyString()
        )).thenReturn(new CancellationResponse(
                CancellationTargetType.PAYMENT,
                id,
                OffsetDateTime.now(ZoneOffset.UTC),
                0
        ));

        mockMvc.perform(post("/api/v1/payments/{id}/cancel", id)
                        .header("Authorization", "Bearer " + token(List.of("PAYMENT_CANCEL")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Cliente solicitou"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowRecurringCancellationWithPermission() throws Exception {
        UUID id = UUID.randomUUID();

        when(cancellationService.cancelRecurringSchedule(
                eq(id),
                eq("Encerrar recorrência"),
                anyString()
        )).thenReturn(new CancellationResponse(
                CancellationTargetType.RECURRING_SCHEDULE,
                id,
                OffsetDateTime.now(ZoneOffset.UTC),
                2
        ));

        mockMvc.perform(post("/api/v1/payments/pix/recurring/{id}/cancel", id)
                        .header("Authorization", "Bearer " + token(List.of("PAYMENT_CANCEL")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Encerrar recorrência"
                                }
                                """))
                .andExpect(status().isOk());
    }

    private String token(List<String> permissions) {
        byte[] keyBytes = SECRET.getBytes(StandardCharsets.UTF_8);
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .subject("security-test-user")
                .claim("permissions", permissions)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
