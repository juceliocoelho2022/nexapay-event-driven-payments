package br.com.nexapay.payment.api;

import br.com.nexapay.payment.config.PaymentSecurityConfig;
import br.com.nexapay.payment.domain.RecurrenceFrequency;
import br.com.nexapay.payment.domain.RecurringScheduleStatus;
import br.com.nexapay.payment.service.RecurringPixScheduleService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecurringPixController.class)
@Import(PaymentSecurityConfig.class)
class RecurringPixControllerSecurityTest {

    private static final String SECRET = "nexapay-local-dev-secret-2026-change-me";
    private static final String ISSUER = "https://nexapay.local/auth";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecurringPixScheduleService service;

    @Test
    void shouldRejectRecurringPixWithoutCreatePermission() throws Exception {
        mockMvc.perform(post("/api/v1/payments/pix/recurring")
                        .header("Authorization", "Bearer " + token(List.of("PAYMENT_READ")))
                        .header("Idempotency-Key", "recurring-security-denied")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowRecurringPixWithCreatePermission() throws Exception {
        UUID id = UUID.randomUUID();
        OffsetDateTime next = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);

        when(service.create(eq("recurring-security"), any(CreateRecurringPixRequest.class)))
                .thenReturn(new RecurringPixScheduleResponse(
                        id,
                        "ACC-SECURITY-REC",
                        "recurring-security@nexapay.test",
                        new BigDecimal("89.90"),
                        "Recurring security test",
                        RecurrenceFrequency.MONTHLY,
                        RecurringScheduleStatus.ACTIVE,
                        next,
                        12,
                        OffsetDateTime.now(ZoneOffset.UTC)
                ));

        mockMvc.perform(post("/api/v1/payments/pix/recurring")
                        .header("Authorization", "Bearer " + token(List.of("PAYMENT_CREATE")))
                        .header("Idempotency-Key", "recurring-security")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldRejectPastFirstOccurrence() throws Exception {
        String body = """
                {
                  "payerAccountId": "ACC-SECURITY-REC",
                  "pixKey": "recurring-security@nexapay.test",
                  "amount": 89.90,
                  "description": "Invalid recurring time",
                  "frequency": "DAILY",
                  "firstOccurrenceAt": "%s",
                  "occurrences": 3
                }
                """.formatted(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));

        mockMvc.perform(post("/api/v1/payments/pix/recurring")
                        .header("Authorization", "Bearer " + token(List.of("PAYMENT_CREATE")))
                        .header("Idempotency-Key", "recurring-security-past")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    private String validBody() {
        return """
                {
                  "payerAccountId": "ACC-SECURITY-REC",
                  "pixKey": "recurring-security@nexapay.test",
                  "amount": 89.90,
                  "description": "Recurring security test",
                  "frequency": "MONTHLY",
                  "firstOccurrenceAt": "%s",
                  "occurrences": 12
                }
                """.formatted(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
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
                .claim("permissions", permissions)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
