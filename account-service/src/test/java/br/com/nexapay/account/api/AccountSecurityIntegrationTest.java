package br.com.nexapay.account.api;

import br.com.nexapay.account.config.AccountSecurityConfig;
import br.com.nexapay.account.domain.AccountStatus;
import br.com.nexapay.account.service.AccountService;
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

@WebMvcTest(AccountController.class)
@Import(AccountSecurityConfig.class)
class AccountSecurityIntegrationTest {

    private static final String SECRET = "nexapay-local-dev-secret-2026-change-me";
    private static final String ISSUER = "https://nexapay.local/auth";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @Test
    void shouldRejectAnonymousAccountRead() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectAccountWriteWithoutPermission() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token(List.of("ACCOUNT_READ")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountNumber": "ACC-SECURITY-01",
                                  "holderName": "Security User"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAccountReadWithPermission() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(accountService.getById(accountId)).thenReturn(accountResponse(accountId));

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId)
                        .header("Authorization", "Bearer " + token(List.of("ACCOUNT_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowAccountCreationWithWritePermission() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(accountService.create(any(CreateAccountRequest.class))).thenReturn(accountResponse(accountId));

        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token(List.of("ACCOUNT_WRITE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountNumber": "ACC-SECURITY-01",
                                  "holderName": "Security User"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldAllowCreditWithWritePermission() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(accountService.credit(eq(accountId), any(AmountRequest.class))).thenReturn(accountResponse(accountId));

        mockMvc.perform(post("/api/v1/accounts/{id}/credit", accountId)
                        .header("Authorization", "Bearer " + token(List.of("ACCOUNT_WRITE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00}"))
                .andExpect(status().isOk());
    }

    private AccountResponse accountResponse(UUID accountId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new AccountResponse(
                accountId,
                "ACC-SECURITY-01",
                "Security User",
                new BigDecimal("100.00"),
                AccountStatus.ACTIVE,
                now,
                now
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
