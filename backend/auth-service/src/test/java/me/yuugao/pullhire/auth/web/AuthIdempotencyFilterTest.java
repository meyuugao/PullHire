package me.yuugao.pullhire.auth.web;

import me.yuugao.pullhire.idempotency.IdempotencyFilter;
import me.yuugao.pullhire.idempotency.IdempotencyResultStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Filter-level tests for the Idempotency-Key middleware on POST /auth/register
 * (docs/api.md §1–§2, decision B): no Spring context, no DB — a standalone
 * MockMvc setup with an in-memory store fake and the problem-json advice.
 */
class AuthIdempotencyFilterTest {

    /** Stand-in for AuthService.register: counts downstream executions. */
    public interface RegisterHandler {
        ResponseEntity<String> register();
    }

    @RestController
    static class ProbeController {

        static RegisterHandler handler;

        @PostMapping("/auth/register")
        ResponseEntity<String> register() {
            return handler.register();
        }

        @PostMapping("/auth/login")
        ResponseEntity<String> login() {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"access_token\":\"t\"}");
        }
    }

    private RegisterHandler handler;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // In-memory store fake (mirrors the idempotency-lib IdempotencyFilterTest
        // pattern): find/save backed by a map, no JdbcTemplate, no DB.
        IdempotencyResultStore store = mock(IdempotencyResultStore.class);
        Map<String, IdempotencyResultStore.StoredResult> mem = new ConcurrentHashMap<>();
        when(store.find(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(mem.get(inv.getArgument(0))));
        doAnswer(inv -> {
            mem.put(inv.getArgument(0), new IdempotencyResultStore.StoredResult(
                    inv.getArgument(1),
                    inv.getArgument(2),
                    inv.getArgument(3) == null ? null : ((byte[]) inv.getArgument(3)).clone()));
            return null;
        }).when(store).save(anyString(), anyInt(), any(), any());

        handler = mock(RegisterHandler.class);
        when(handler.register()).thenReturn(ResponseEntity.status(201)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"id\":1}"));
        ProbeController.handler = handler;

        // Decision B scoping: only /auth/register requires the key; /auth/login,
        // /auth/refresh, /auth/verify-email and /consents* stay key-less.
        IdempotencyFilter filter = new IdempotencyFilter(store, List.of("/auth/register"));
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .addFilters(filter)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void registerWithoutKeyReturns409NestedProblemJson() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"Secret-1\",\"role\":\"candidate\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("conflict"));
        verify(handler, never()).register();
    }

    @Test
    void registerWithKeyExecutesOnceAndReplaysIdentically() throws Exception {
        MvcResult first = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-1")
                        .content("{\"email\":\"a@example.com\",\"password\":\"Secret-1\",\"role\":\"candidate\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult replay = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-1")
                        .content("{\"email\":\"a@example.com\",\"password\":\"Secret-1\",\"role\":\"candidate\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(replay.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
        verify(handler, times(1)).register();
    }

    @Test
    void loginWithoutKeyPassesThrough() throws Exception {
        // Scoping proof: /auth/login is outside path-patterns, so no key needed.
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"Secret-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("t"));
        verify(handler, never()).register();
    }
}
