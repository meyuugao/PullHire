package me.yuugao.pullhire.employer.web;

import me.yuugao.pullhire.employer.application.CompanyService;
import me.yuugao.pullhire.employer.domain.Company;
import me.yuugao.pullhire.employer.error.ApiException;
import me.yuugao.pullhire.employer.security.JwksKeyProvider;
import me.yuugao.pullhire.employer.security.JwtAuthFilter;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc coverage for {@code /employer/company}: the JWT-role gate
 * (401/403), GET/PUT happy paths with the snake_case wire against a mocked
 * {@link CompanyService}, the 404-before-first-PUT envelope, and {@code @Valid}
 * failures surfacing as the unified 400 {@code validation_error} envelope.
 */
class CompanyControllerTest {

    private static final String PUT_BODY = "{\"name\":\"Acme\",\"description\":\"About us\","
            + "\"industry\":\"IT\",\"contact_email\":\"hr@acme.example\","
            + "\"contact_phone\":\"+7\",\"website\":\"https://acme.example\"}";

    private CompanyService service;
    private MockMvc mockMvc;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        service = mock(CompanyService.class);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        JwksKeyProvider keys = mock(JwksKeyProvider.class);
        when(keys.publicKey(any())).thenReturn((RSAPublicKey) keyPair.getPublic());

        mockMvc = MockMvcBuilders.standaloneSetup(new CompanyController(service))
                .addFilters(new JwtAuthFilter(keys, new JsonMapper()))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void missingTokenIs401Unauthenticated() throws Exception {
        mockMvc.perform(get("/employer/company"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("unauthenticated"));
    }

    @Test
    void nonEmployerRoleIs403Forbidden() throws Exception {
        mockMvc.perform(get("/employer/company").header("Authorization", "Bearer " + token(1L, "candidate")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("forbidden"));
    }

    @Test
    void getReturnsOwnerProfile() throws Exception {
        when(service.getRequired(1L)).thenReturn(company(7L, 1L, "Acme"));

        mockMvc.perform(get("/employer/company").header("Authorization", "Bearer " + token(1L, "employer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("7"))
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.contact_email").value("hr@acme.example"))
                .andExpect(jsonPath("$.contact_phone").value("+7"))
                .andExpect(jsonPath("$.created_at").exists())
                .andExpect(jsonPath("$.updated_at").exists());
    }

    @Test
    void getBeforeAnyPutIs404NotFound() throws Exception {
        when(service.getRequired(1L)).thenThrow(ApiException.notFound("Профиль компании не найден"));

        mockMvc.perform(get("/employer/company").header("Authorization", "Bearer " + token(1L, "employer")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("not_found"));
    }

    @Test
    void putCreatesRowAndReturns200() throws Exception {
        when(service.upsert(eq(1L), any())).thenReturn(company(7L, 1L, "Acme"));

        mockMvc.perform(put("/employer/company")
                        .header("Authorization", "Bearer " + token(1L, "employer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PUT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("7"))
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.contact_email").value("hr@acme.example"));
    }

    @Test
    void putSecondTimeUpdatesSameRow() throws Exception {
        when(service.upsert(eq(1L), any())).thenReturn(company(7L, 1L, "Acme v2"));

        mockMvc.perform(put("/employer/company")
                        .header("Authorization", "Bearer " + token(1L, "employer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PUT_BODY.replace("Acme", "Acme v2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("7"))
                .andExpect(jsonPath("$.name").value("Acme v2"));
    }

    @Test
    void putForOtherUserIsOwnerScoped() throws Exception {
        when(service.upsert(eq(2L), any())).thenReturn(company(9L, 2L, "Beta"));

        mockMvc.perform(put("/employer/company")
                        .header("Authorization", "Bearer " + token(2L, "employer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Beta\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("9"));

        verify(service).upsert(eq(2L), any());
    }

    @Test
    void blankNameYieldsValidationErrorEnvelope() throws Exception {
        mockMvc.perform(put("/employer/company")
                        .header("Authorization", "Bearer " + token(1L, "employer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }

    @Test
    void badEmailYieldsValidationErrorEnvelope() throws Exception {
        mockMvc.perform(put("/employer/company")
                        .header("Authorization", "Bearer " + token(1L, "employer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\",\"contact_email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }

    private String token(long subject, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(Long.toString(subject))
                .issuer("pullhire-auth")
                .claim("realm_access", Map.of("roles", List.of(role)))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static Company company(long id, long userId, String name) {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        return new Company(id, userId, name, "About us", "IT", "hr@acme.example", "+7",
                "https://acme.example", now, now);
    }
}
