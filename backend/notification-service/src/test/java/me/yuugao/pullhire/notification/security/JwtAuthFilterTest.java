package me.yuugao.pullhire.notification.security;

import me.yuugao.pullhire.notification.web.ApiExceptionHandler;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Filter-level tests for the 401 contract: no network/Postgres/RabbitMQ. The
 * provider is seeded with a static public key; foreign tokens are signed by a
 * different key.
 */
class JwtAuthFilterTest {

    private static final String ISSUER = "pullhire-auth";

    private KeyPair server;
    private KeyPair foreign;
    private MockMvc mockMvc;

    @RestController
    static class ProbeController {

        @GetMapping("/protected")
        ResponseEntity<Void> protectedEndpoint() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/healthz")
        ResponseEntity<Void> health() {
            return ResponseEntity.ok().build();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        server = generator.generateKeyPair();
        foreign = generator.generateKeyPair();

        JwksKeyProvider provider = new JwksKeyProvider(null, (RSAPublicKey) server.getPublic());
        JwtAuthFilter filter = new JwtAuthFilter(provider, new JsonMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .addFilters(filter)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private String token(KeyPair signer, Instant expiresAt) {
        return Jwts.builder()
                .subject("42")
                .issuer(ISSUER)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiresAt))
                .signWith((RSAPrivateKey) signer.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    @Test
    void missingBearerReturns401ProblemJson() throws Exception {
        mockMvc.perform(get("/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("unauthenticated"));
    }

    @Test
    void expiredBearerReturns401() throws Exception {
        String expired = token(server, Instant.now().minusSeconds(60));

        mockMvc.perform(get("/protected").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthenticated"));
    }

    @Test
    void foreignKeyBearerReturns401() throws Exception {
        String foreignToken = token(foreign, Instant.now().plusSeconds(300));

        mockMvc.perform(get("/protected").header("Authorization", "Bearer " + foreignToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthenticated"));
    }

    @Test
    void malformedBearerReturns401() throws Exception {
        mockMvc.perform(get("/protected").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthenticated"));
    }

    @Test
    void validBearerReachesController() throws Exception {
        String valid = token(server, Instant.now().plusSeconds(300));

        mockMvc.perform(get("/protected").header("Authorization", "Bearer " + valid))
                .andExpect(status().isOk());
    }

    @Test
    void publicPathWithoutTokenIsAllowed() throws Exception {
        mockMvc.perform(get("/healthz")).andExpect(status().isOk());
    }
}
