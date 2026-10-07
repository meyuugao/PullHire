package com.pullhire.auth.security;

import com.pullhire.auth.application.TokenService;
import com.pullhire.auth.config.JwtProperties;
import com.pullhire.auth.config.RsaKeyProvider;
import com.pullhire.auth.domain.Role;
import com.pullhire.auth.domain.User;
import com.pullhire.auth.domain.UserStatus;
import com.pullhire.auth.repo.RefreshTokenRepository;
import com.pullhire.auth.repo.UserRepository;
import com.pullhire.auth.web.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Filter-level tests for the 401 contract: no Postgres/RabbitMQ involved, the
 * context is a standalone MockMvc setup with the filter and problem-json advice.
 */
class JwtAuthFilterTest {

    private JwtProperties properties;
    private TokenService tokenService;
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
    void setUp() {
        properties = new JwtProperties();
        properties.setAccessTtl(Duration.ofMinutes(15));
        properties.setRefreshTtl(Duration.ofDays(30));
        tokenService = new TokenService(
                new RsaKeyProvider(null, null),
                properties,
                mock(RefreshTokenRepository.class),
                mock(UserRepository.class));
        JwtAuthFilter filter = new JwtAuthFilter(tokenService, new JsonMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .addFilters(filter)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private static User user() {
        Instant now = Instant.now();
        return new User(1L, "user@example.com", "hash", Role.CANDIDATE, UserStatus.ACTIVE, now, null, now, now);
    }

    @Test
    void missingBearerReturns401ProblemJson() throws Exception {
        mockMvc.perform(get("/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("unauthenticated"));
    }

    @Test
    void malformedBearerReturns401() throws Exception {
        mockMvc.perform(get("/protected").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthenticated"));
    }

    @Test
    void expiredBearerReturns401() throws Exception {
        properties.setAccessTtl(Duration.ofMinutes(-1));
        String expired = tokenService.issueAccessToken(user());

        mockMvc.perform(get("/protected").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthenticated"));
    }

    @Test
    void validBearerReachesController() throws Exception {
        String token = tokenService.issueAccessToken(user());

        mockMvc.perform(get("/protected").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void publicPathWithoutTokenIsAllowed() throws Exception {
        mockMvc.perform(get("/healthz")).andExpect(status().isOk());
    }
}
