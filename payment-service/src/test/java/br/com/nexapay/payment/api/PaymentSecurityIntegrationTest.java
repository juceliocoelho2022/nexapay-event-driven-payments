package br.com.nexapay.payment.api;

import br.com.nexapay.payment.config.PaymentSecurityConfig;
import br.com.nexapay.payment.service.PaymentService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import(PaymentSecurityConfig.class)
class PaymentSecurityIntegrationTest {

    private static final String SECRET = "nexapay-local-dev-secret-2026-change-me";
    private static final String ISSUER = "https://nexapay.local/auth";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @Test
    void shouldRejectAnonymousPaymentRead() throws Exception {
        mockMvc.perform(get("/api/v1/payments/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectPaymentReadWithoutPermission() throws Exception {
        mockMvc.perform(get("/api/v1/payments/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token(List.of("PAYMENT_CREATE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowPaymentReadWithPermission() throws Exception {
        UUID paymentId = UUID.randomUUID();
        when(paymentService.findById(paymentId)).thenReturn(paymentResponse(paymentId));

        mockMvc.perform(get("/api/v1/payments/{id}", paymentId)
                        .header("Authorization", "Bearer " + token(List.of("PAYMENT_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowPaymentCreationWithPermission() throws Exception {
        UUID paymentId = UUID.randomUUID();
        when(paymentService.createPixPayment(eq("security-test"), any(CreatePixPaymentRequest.class)))
                .thenReturn(paymentResponse(paymentId));

        mockMvc.perform(post("/api/v1/payments/pix")
                        .header("Authorization", "Bearer " + token(List.of("PAYMENT_CREATE")))
                        .header("Idempotency-Key", "security-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payerAccountId": "ACC-SECURITY-01",
                                  "pixKey": "security@nexapay.com",
                                  "amount": 25.00,
                                  "description": "JWT authorization test"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    private PaymentResponse paymentResponse(UUID paymentId) {
        return new PaymentResponse(
                paymentId,
                "ACC-SECURITY-01",
                "security@nexapay.com",
                new BigDecimal("25.00"),
                "JWT authorization test",
                null,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
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
                .subject(UUID.randomUUID().toString())
                .claim("email", "security-test@nexapay.com")
                .claim("permissions", permissions)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
