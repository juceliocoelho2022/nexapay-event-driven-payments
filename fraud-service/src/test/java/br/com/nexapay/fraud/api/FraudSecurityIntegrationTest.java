package br.com.nexapay.fraud.api;

import br.com.nexapay.fraud.config.FraudSecurityConfig;
import br.com.nexapay.fraud.domain.FraudDecision;
import br.com.nexapay.fraud.domain.FraudDecisionType;
import br.com.nexapay.fraud.service.FraudService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FraudController.class)
@Import(FraudSecurityConfig.class)
class FraudSecurityIntegrationTest {

    private static final String SECRET = "nexapay-local-dev-secret-2026-change-me";
    private static final String ISSUER = "https://nexapay.local/auth";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FraudService fraudService;

    @Test
    void shouldRejectAnonymousFraudRead() throws Exception {
        mockMvc.perform(get("/api/v1/fraud/payments/{paymentId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectFraudReadWithoutPermission() throws Exception {
        mockMvc.perform(get("/api/v1/fraud/payments/{paymentId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token(List.of("PAYMENT_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowFraudReadWithPermission() throws Exception {
        UUID paymentId = UUID.randomUUID();
        FraudDecision decision = FraudDecision.create(
                UUID.randomUUID(),
                paymentId,
                "ACC-FRAUD-SECURITY-01",
                "security@nexapay.com",
                new BigDecimal("12000.00"),
                FraudDecisionType.BLOCKED,
                95,
                "High amount",
                OffsetDateTime.parse("2026-08-16T18:00:00Z")
        );
        when(fraudService.findByPaymentId(paymentId)).thenReturn(Optional.of(decision));

        mockMvc.perform(get("/api/v1/fraud/payments/{paymentId}", paymentId)
                        .header("Authorization", "Bearer " + token(List.of("FRAUD_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.decision").value("BLOCKED"))
                .andExpect(jsonPath("$.riskScore").value(95));
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
