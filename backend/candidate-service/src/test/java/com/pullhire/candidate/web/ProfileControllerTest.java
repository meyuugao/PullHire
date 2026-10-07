package com.pullhire.candidate.web;

import com.pullhire.candidate.application.ProfileService;
import com.pullhire.candidate.domain.Profile;
import com.pullhire.candidate.security.JwksKeyProvider;
import com.pullhire.candidate.security.JwtAuthFilter;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc coverage for {@code /candidate/profile}: the JWT-role gate
 * (401/403) plus GET/PUT happy paths against a mocked {@link ProfileService}.
 */
class ProfileControllerTest {

    private ProfileService service;
    private MockMvc mockMvc;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        service = mock(ProfileService.class);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        JwksKeyProvider keys = mock(JwksKeyProvider.class);
        when(keys.publicKey(any())).thenReturn((RSAPublicKey) keyPair.getPublic());

        mockMvc = MockMvcBuilders.standaloneSetup(new ProfileController(service))
                .addFilters(new JwtAuthFilter(keys, new JsonMapper()))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void missingTokenIs401Unauthenticated() throws Exception {
        mockMvc.perform(get("/candidate/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("unauthenticated"));
    }

    @Test
    void nonCandidateRoleIs403Forbidden() throws Exception {
        mockMvc.perform(get("/candidate/profile").header("Authorization", "Bearer " + token("employer")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("forbidden"));
    }

    @Test
    void candidateGetReturnsProfile() throws Exception {
        when(service.getRequired(1L)).thenReturn(profile());

        mockMvc.perform(get("/candidate/profile").header("Authorization", "Bearer " + token("candidate")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ivan Petrov"))
                .andExpect(jsonPath("$.status").value("published"));
    }

    @Test
    void candidatePutUpdatesProfile() throws Exception {
        when(service.upsert(eq(1L), any())).thenReturn(profile());

        mockMvc.perform(put("/candidate/profile")
                        .header("Authorization", "Bearer " + token("candidate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Ivan Petrov\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ivan Petrov"));
    }

    private String token(String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("1")
                .issuer("pullhire-auth")
                .claim("realm_access", Map.of("roles", List.of(role)))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static Profile profile() {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        return new Profile(7L, 1L, "Ivan Petrov", null, null, null, null, null, null, null, null,
                null, null, null, "published", now, now);
    }
}
