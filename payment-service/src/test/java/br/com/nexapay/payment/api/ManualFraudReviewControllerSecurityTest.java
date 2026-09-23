package br.com.nexapay.payment.api;

import br.com.nexapay.payment.config.PaymentSecurityConfig;
import br.com.nexapay.payment.domain.ManualFraudReviewDecision;
import br.com.nexapay.payment.domain.PaymentStatus;
import br.com.nexapay.payment.service.ManualFraudReviewService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ManualFraudReviewController.class)
@Import(PaymentSecurityConfig.class)
class ManualFraudReviewControllerSecurityTest {

    private static final String SECRET = "nexapay-local-dev-secret-2026-change-me";
    private static final String ISSUER = "https://nexapay.local/auth";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ManualFraudReviewService service;

    @Test
    void shouldRejectReviewWithoutFraudReviewPermission() throws Exception {
        mockMvc.perform(post("/api/v1/payments/{id}/fraud-review", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token(List.of("PAYMENT_READ")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "reason": "Sem permissão"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowReviewWithFraudReviewPermission() throws Exception {
        UUID paymentId = UUID.randomUUID();

        when(service.review(
                eq(paymentId),
                eq(ManualFraudReviewDecision.APPROVE),
                eq("Validado manualmente"),
                anyString()
        )).thenReturn(new ManualFraudReviewResponse(
                paymentId,
                ManualFraudReviewDecision.APPROVE,
                PaymentStatus.REVIEW,
                PaymentStatus.COMPLETED,
                "fraud-reviewer",
                OffsetDateTime.now(ZoneOffset.UTC)
        ));

        mockMvc.perform(post("/api/v1/payments/{id}/fraud-review", paymentId)
                        .header("Authorization", "Bearer " + token(List.of("FRAUD_REVIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "reason": "Validado manualmente"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowHistoryWithPaymentReadPermission() throws Exception {
        UUID paymentId = UUID.randomUUID();

        when(service.history(paymentId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/payments/{id}/fraud-review/history", paymentId)
                        .header("Authorization", "Bearer " + token(List.of("PAYMENT_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectHistoryWithoutPaymentReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/payments/{id}/fraud-review/history", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token(List.of("FRAUD_REVIEW"))))
                .andExpect(status().isForbidden());
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
                .subject("fraud-review-security-test")
                .claim("permissions", permissions)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
