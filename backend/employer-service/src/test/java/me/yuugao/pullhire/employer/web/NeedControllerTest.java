package me.yuugao.pullhire.employer.web;

import me.yuugao.pullhire.employer.application.NeedService;
import me.yuugao.pullhire.employer.domain.Need;
import me.yuugao.pullhire.employer.domain.NeedCommand;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc coverage for {@code /employer/needs}: the 201/200/204 happy
 * paths with the snake_case wire against a mocked {@link NeedService}, the
 * 404/403 owner checks, the 404-before-profile envelope, and {@code @Valid} plus
 * status failures surfacing as the unified 400 {@code validation_error} envelope.
 */
class NeedControllerTest {

    private static final String BODY = "{\"title\":\"Backend\",\"description\":\"About\","
            + "\"specialization_code\":\"backend\",\"grade\":\"middle\","
            + "\"stack\":[\"java\"],\"status\":\"active\"}";

    private NeedService service;
    private MockMvc mockMvc;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        service = mock(NeedService.class);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        JwksKeyProvider keys = mock(JwksKeyProvider.class);
        when(keys.publicKey(any())).thenReturn((RSAPublicKey) keyPair.getPublic());

        mockMvc = MockMvcBuilders.standaloneSetup(new NeedController(service))
                .addFilters(new JwtAuthFilter(keys, new JsonMapper()))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void postCreatesNeedWith201() throws Exception {
        when(service.create(eq(1L), any(NeedCommand.class))).thenReturn(need(11L, 7L, "Backend", "active"));

        mockMvc.perform(post("/employer/needs")
                        .header("Authorization", "Bearer " + token(1L, "employer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("11"))
                .andExpect(jsonPath("$.title").value("Backend"))
                .andExpect(jsonPath("$.specialization_code").value("backend"))
                .andExpect(jsonPath("$.stack[0]").value("java"))
                .andExpect(jsonPath("$.created_at").exists())
                .andExpect(jsonPath("$.updated_at").exists());
    }

    @Test
    void listReturnsPaginatedEnvelopeForOwner() throws Exception {
        when(service.list(eq(1L), anyInt(), anyInt(), anyString()))
                .thenReturn(new NeedService.NeedPage(List.of(need(11L, 7L, "Backend", "active")), 0, 20, 1L));

        mockMvc.perform(get("/employer/needs?page=0&size=20")
                        .header("Authorization", "Bearer " + token(1L, "employer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("11"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void getByIdReturnsOwnerNeed() throws Exception {
        when(service.get(1L, 11L)).thenReturn(need(11L, 7L, "Backend", "active"));

        mockMvc.perform(get("/employer/needs/11")
                        .header("Authorization", "Bearer " + token(1L, "employer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("11"));
    }

    @Test
    void getUnknownIdIs404NotFound() throws Exception {
        when(service.get(1L, 99L)).thenThrow(ApiException.notFound("Потребность не найдена"));

        mockMvc.perform(get("/employer/needs/99")
                        .header("Authorization", "Bearer " + token(1L, "employer")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("not_found"));
    }

    @Test
    void getForeignNeedIs403Forbidden() throws Exception {
        when(service.get(2L, 11L)).thenThrow(ApiException.forbidden("Потребность принадлежит другой компании"));

        mockMvc.perform(get("/employer/needs/11")
                        .header("Authorization", "Bearer " + token(2L, "employer")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("forbidden"));
    }

    @Test
    void putUpdatesAndReturns200() throws Exception {
        when(service.update(eq(1L), eq(11L), any(NeedCommand.class)))
                .thenReturn(need(11L, 7L, "Backend", "archived"));

        mockMvc.perform(put("/employer/needs/11")
                        .header("Authorization", "Bearer " + token(1L, "employer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("\"active\"", "\"archived\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("11"))
                .andExpect(jsonPath("$.status").value("archived"));
    }

    @Test
    void putForeignIs403() throws Exception {
        when(service.update(eq(2L), eq(11L), any(NeedCommand.class)))
                .thenThrow(ApiException.forbidden("Потребность принадлежит другой компании"));

        mockMvc.perform(put("/employer/needs/11")
                        .header("Authorization", "Bearer " + token(2L, "employer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("forbidden"));
    }

    @Test
    void putUnknownIs404() throws Exception {
        when(service.update(eq(1L), eq(99L), any(NeedCommand.class)))
                .thenThrow(ApiException.notFound("Потребность не найдена"));

        mockMvc.perform(put("/employer/needs/99")
                        .header("Authorization", "Bearer " + token(1L, "employer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("not_found"));
    }

    @Test
    void deleteOwnerReturns204() throws Exception {
        mockMvc.perform(delete("/employer/needs/11")
                        .header("Authorization", "Bearer " + token(1L, "employer")))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteForeignIs403() throws Exception {
        doThrow(ApiException.forbidden("Потребность принадлежит другой компании"))
                .when(service).delete(2L, 11L);

        mockMvc.perform(delete("/employer/needs/11")
                        .header("Authorization", "Bearer " + token(2L, "employer")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("forbidden"));
    }

    @Test
    void deleteUnknownIs404() throws Exception {
        doThrow(ApiException.notFound("Потребность не найдена"))
                .when(service).delete(1L, 99L);

        mockMvc.perform(delete("/employer/needs/99")
                        .header("Authorization", "Bearer " + token(1L, "employer")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("not_found"));
    }

    @Test
    void callerWithoutCompanyIs404() throws Exception {
        when(service.create(eq(5L), any(NeedCommand.class)))
                .thenThrow(ApiException.notFound("Профиль компании не найден"));

        mockMvc.perform(post("/employer/needs")
                        .header("Authorization", "Bearer " + token(5L, "employer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("not_found"));
    }

    @Test
    void blankTitleIsValidationError() throws Exception {
        mockMvc.perform(post("/employer/needs")
                        .header("Authorization", "Bearer " + token(1L, "employer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \",\"specialization_code\":\"backend\",\"grade\":\"middle\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }

    @Test
    void invalidStatusIsValidationError() throws Exception {
        when(service.create(eq(1L), any(NeedCommand.class)))
                .thenThrow(ApiException.validation("status должен быть одним из: active, archived"));

        mockMvc.perform(post("/employer/needs")
                        .header("Authorization", "Bearer " + token(1L, "employer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("\"active\"", "\"draft\"")))
                .andExpect(status().isBadRequest())
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

    private static Need need(long id, long companyId, String title, String status) {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        return new Need(id, companyId, title, "About", "backend", "middle",
                new String[]{"java"}, status, now, now);
    }
}
