package br.com.nexapay.payment.api;

import br.com.nexapay.payment.config.PaymentSecurityConfig;
import br.com.nexapay.payment.domain.FraudReviewPriority;
import br.com.nexapay.payment.service.FraudReviewQueueService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FraudReviewQueueController.class)
@Import(PaymentSecurityConfig.class)
class FraudReviewQueueControllerSecurityTest {

    private static final String SECRET = "nexapay-local-dev-secret-2026-change-me";
    private static final String ISSUER = "https://nexapay.local/auth";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FraudReviewQueueService service;

    @Test
    void shouldRejectQueueWithoutFraudReviewPermission() throws Exception {
        mockMvc.perform(get("/api/v1/fraud-review/cases")
                        .header("Authorization", "Bearer " + token(List.of("PAYMENT_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowQueueWithFraudReviewPermission() throws Exception {
        when(service.listOpenCases(null, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/fraud-review/cases")
                        .header("Authorization", "Bearer " + token(List.of("FRAUD_REVIEW"))))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowClaimAndReleaseWithFraudReviewPermission() throws Exception {
        UUID paymentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        FraudReviewCaseResponse response = new FraudReviewCaseResponse(
                paymentId,
                null,
                "review@nexapay.test",
                70,
                "Manual review required",
                now,
                "fraud-review-security-test",
                now,
                now.plusMinutes(15),
                false,
                FraudReviewPriority.P2,
                now.plusMinutes(30),
                false,
                null,
                0,
                1800
        );

        when(service.claim(eq(paymentId), anyString()))
                .thenReturn(response);
        when(service.release(eq(paymentId), anyString()))
                .thenReturn(response);

        String token = token(List.of("FRAUD_REVIEW"));

        mockMvc.perform(post("/api/v1/fraud-review/cases/{id}/claim", paymentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/fraud-review/cases/{id}/release", paymentId)
                        .header("Authorization", "Bearer " + token))
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
                .subject("fraud-review-security-test")
                .claim("permissions", permissions)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
