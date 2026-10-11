package me.yuugao.pullhire.assessment.web;

import me.yuugao.pullhire.assessment.application.CandidateProfileClient;
import me.yuugao.pullhire.assessment.application.SurveyService;
import me.yuugao.pullhire.assessment.error.ApiException;
import me.yuugao.pullhire.assessment.security.JwksKeyProvider;
import me.yuugao.pullhire.assessment.security.JwtAuthFilter;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc coverage for {@code POST /assessment/survey}: the JWT role
 * gate (401/403), the happy path against a mocked {@link SurveyService}, and the
 * {@code @Valid} failure surfacing as the unified 400 {@code validation_error}
 * envelope.
 */
class AssessmentSurveyControllerTest {

    private static final String BODY = "{\"industry_code\":\"industry-1\","
            + "\"specialization_code\":\"spec-1\",\"target_grade\":\"middle\","
            + "\"declared_dimensions\":[\"middle\"],\"survey_answers\":{\"q1\":\"a\"}}";

    private SurveyService surveys;
    private MockMvc mockMvc;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        surveys = mock(SurveyService.class);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        JwksKeyProvider keys = mock(JwksKeyProvider.class);
        when(keys.publicKey(any())).thenReturn((RSAPublicKey) keyPair.getPublic());

        CandidateProfileClient profiles = mock(CandidateProfileClient.class);
        when(profiles.resolveProfileId(1L)).thenReturn(1L);

        mockMvc = MockMvcBuilders.standaloneSetup(new AssessmentSurveyController(surveys))
                .addFilters(new JwtAuthFilter(keys, new JsonMapper(), profiles))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void createsSessionAndReturnsItsId() throws Exception {
        when(surveys.submit(eq(1L), any())).thenReturn(77L);

        mockMvc.perform(post("/assessment/survey")
                        .header("Authorization", "Bearer " + token("candidate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session_id").value("77"));
    }

    @Test
    void missingTokenIs401Unauthenticated() throws Exception {
        mockMvc.perform(post("/assessment/survey")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("unauthenticated"));
    }

    @Test
    void nonCandidateRoleIs403Forbidden() throws Exception {
        mockMvc.perform(post("/assessment/survey")
                        .header("Authorization", "Bearer " + token("employer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("forbidden"));
    }

    @Test
    void missingRequiredAnswersYieldsValidationErrorEnvelope() throws Exception {
        String missingAnswers = "{\"specialization_code\":\"spec-1\",\"target_grade\":\"middle\","
                + "\"declared_dimensions\":[\"middle\"]}";

        mockMvc.perform(post("/assessment/survey")
                        .header("Authorization", "Bearer " + token("candidate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingAnswers))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }

    @Test
    void blankSpecializationYieldsValidationErrorEnvelope() throws Exception {
        String blankSpec = "{\"specialization_code\":\"\",\"target_grade\":\"middle\","
                + "\"declared_dimensions\":[\"middle\"],\"survey_answers\":{\"q\":1}}";

        mockMvc.perform(post("/assessment/survey")
                        .header("Authorization", "Bearer " + token("candidate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blankSpec))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }

    @Test
    void unknownTaxonomyCodeYieldsValidationErrorEnvelope() throws Exception {
        when(surveys.submit(eq(1L), any())).thenThrow(ApiException.validation("Unknown specialization_code: nope"));

        mockMvc.perform(post("/assessment/survey")
                        .header("Authorization", "Bearer " + token("candidate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
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
