package br.com.nexapay.ledger.api;

import br.com.nexapay.ledger.config.LedgerSecurityConfig;
import br.com.nexapay.ledger.service.LedgerService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LedgerController.class)
@Import(LedgerSecurityConfig.class)
class LedgerSecurityIntegrationTest {

    private static final String SECRET = "nexapay-local-dev-secret-2026-change-me";
    private static final String ISSUER = "https://nexapay.local/auth";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LedgerService ledgerService;

    @Test
    void shouldRejectAnonymousLedgerRead() throws Exception {
        mockMvc.perform(get("/api/v1/ledger/accounts/{accountId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectLedgerReadWithoutPermission() throws Exception {
        mockMvc.perform(get("/api/v1/ledger/accounts/{accountId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token(List.of("ACCOUNT_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowLedgerReadWithPermission() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(ledgerService.getEntriesByAccountId(eq(accountId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/ledger/accounts/{accountId}", accountId)
                        .header("Authorization", "Bearer " + token(List.of("LEDGER_READ"))))
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
                .subject(UUID.randomUUID().toString())
                .claim("email", "security-test@nexapay.com")
                .claim("permissions", permissions)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
