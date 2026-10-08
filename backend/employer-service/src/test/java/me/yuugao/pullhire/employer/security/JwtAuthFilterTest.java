package me.yuugao.pullhire.employer.security;

import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import me.yuugao.pullhire.employer.web.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc coverage for the employer JWT gate: no Postgres/RabbitMQ,
 * mocked {@link JwksKeyProvider} with a local RS256 key pair.
 */
class JwtAuthFilterTest {

    private MockMvc mockMvc;
    private KeyPair keyPair;

    @RestController
    static class ProbeController {

        @GetMapping("/employer/ping")
        ResponseEntity<Map<String, Object>> ping(HttpServletRequest request) {
            return ResponseEntity.ok(Map.of(
                    "user_id", CurrentUser.id(request),
                    "role", String.valueOf(request.getAttribute(JwtAuthFilter.ATTR_ROLE))));
        }

        @GetMapping("/healthz")
        ResponseEntity<Void> health() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/v3/api-docs")
        ResponseEntity<Void> docs() {
            return ResponseEntity.ok().build();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        JwksKeyProvider keys = mock(JwksKeyProvider.class);
        when(keys.publicKey(any())).thenReturn((RSAPublicKey) keyPair.getPublic());

        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .addFilters(new JwtAuthFilter(keys, new JsonMapper()))
                .build();
    }

    @Test
    void missingTokenIs401Unauthenticated() throws Exception {
        mockMvc.perform(get("/employer/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("unauthenticated"));
    }

    @Test
    void expiredTokenIs401Unauthenticated() throws Exception {
        mockMvc.perform(get("/employer/ping")
                        .header("Authorization", "Bearer " + token("employer", Instant.now().minusSeconds(600))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("unauthenticated"));
    }

    @Test
    void wrongRoleIs403Forbidden() throws Exception {
        mockMvc.perform(get("/employer/ping")
                        .header("Authorization", "Bearer " + token("candidate", Instant.now().plusSeconds(300))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("forbidden"));
    }

    @Test
    void validEmployerTokenSetsAttributes() throws Exception {
        MvcResult result = mockMvc.perform(get("/employer/ping")
                        .header("Authorization", "Bearer " + token("employer", Instant.now().plusSeconds(300))))
                .andExpect(status().isOk())
                .andReturn();

        // Controller echoes the filter-set attributes; CurrentUser.id must resolve sub=7.
        assertThat(result.getResponse().getContentAsString()).contains("\"user_id\":7");
        assertThat(result.getResponse().getContentAsString()).contains("\"role\":\"employer\"");
    }

    @Test
    void publicHealthPassesWithoutToken() throws Exception {
        mockMvc.perform(get("/healthz")).andExpect(status().isOk());
    }

    @Test
    void publicApiDocsPassWithoutToken() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    private String token(String role, Instant expiration) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("7")
                .issuer("pullhire-auth")
                .claim("realm_access", Map.of("roles", List.of(role)))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }
}
