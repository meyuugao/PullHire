package me.yuugao.pullhire.assessment.web;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import io.jsonwebtoken.Jwts;
import me.yuugao.pullhire.assessment.application.GradeChoiceService;
import me.yuugao.pullhire.assessment.application.GradeChoiceService.Outcome;
import me.yuugao.pullhire.assessment.error.ApiException;
import me.yuugao.pullhire.assessment.security.JwksKeyProvider;
import me.yuugao.pullhire.assessment.security.JwtAuthFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc coverage for {@code POST /assessment/grade-choice}:
 * snake_case wire with stringified ids, the JWT gate, {@code @Valid}
 * failures, and service-level {@code validation_error} mapping.
 */
class AssessmentGradeControllerTest {

    private static final String BODY = "{\"session_id\":\"7\",\"declared_grade\":\"senior\"}";

    private GradeChoiceService grades;
    private MockMvc mockMvc;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        grades = mock(GradeChoiceService.class);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        JwksKeyProvider keys = mock(JwksKeyProvider.class);
        when(keys.publicKey(any())).thenReturn((RSAPublicKey) keyPair.getPublic());

        mockMvc = MockMvcBuilders.standaloneSetup(new AssessmentGradeController(grades))
                .addFilters(new JwtAuthFilter(keys, new JsonMapper()))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void fixesGradeAndReturnsSnakeCaseStringIds() throws Exception {
        when(grades.choose(eq(7L), eq(1L), eq("senior"))).thenReturn(new Outcome(7L, "senior", 0.5));

        mockMvc.perform(post("/assessment/grade-choice")
                        .header("Authorization", "Bearer " + token("candidate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session_id").value("7"))
                .andExpect(jsonPath("$.declared_grade").value("senior"))
                .andExpect(jsonPath("$.start_theta").value(0.5));
    }

    @Test
    void missingTokenIs401Unauthenticated() throws Exception {
        mockMvc.perform(post("/assessment/grade-choice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("unauthenticated"));
    }

    @Test
    void blankGradeYieldsValidationErrorEnvelope() throws Exception {
        mockMvc.perform(post("/assessment/grade-choice")
                        .header("Authorization", "Bearer " + token("candidate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"session_id\":\"7\",\"declared_grade\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }

    @Test
    void unknownGradeYieldsValidationErrorEnvelope() throws Exception {
        when(grades.choose(anyLong(), anyLong(), anyString()))
                .thenThrow(ApiException.validation("Unknown declared_grade: staff++"));

        mockMvc.perform(post("/assessment/grade-choice")
                        .header("Authorization", "Bearer " + token("candidate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"session_id\":\"7\",\"declared_grade\":\"staff++\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }

    @Test
    void nonNumericSessionIdYieldsValidationErrorEnvelope() throws Exception {
        mockMvc.perform(post("/assessment/grade-choice")
                        .header("Authorization", "Bearer " + token("candidate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"session_id\":\"nope\",\"declared_grade\":\"senior\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));
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
}
