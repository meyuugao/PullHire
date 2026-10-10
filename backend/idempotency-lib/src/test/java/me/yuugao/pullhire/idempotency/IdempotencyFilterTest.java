package me.yuugao.pullhire.idempotency;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** No Spring context, no DB: filter 409-on-missing-key + replay logic with a mocked store. */
class IdempotencyFilterTest {

    @Test
    void missingKeyReturns409NestedEnvelope() throws Exception {
        IdempotencyResultStore store = mock(IdempotencyResultStore.class);
        IdempotencyFilter filter = new IdempotencyFilter(store, List.of());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/offers");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString())
                .contains("\"error\"")
                .contains("\"code\":\"conflict\"");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void knownKeyReplaysStoredResponse() throws Exception {
        byte[] body = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);
        IdempotencyResultStore store = mock(IdempotencyResultStore.class);
        when(store.find("k-1")).thenReturn(
                Optional.of(new IdempotencyResultStore.StoredResult(201, "application/json", body)));
        IdempotencyFilter filter = new IdempotencyFilter(store, List.of());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/offers");
        request.addHeader("Idempotency-Key", "k-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsByteArray()).isEqualTo(body);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void successIsPersistedAndErrorIsNot() throws Exception {
        // 2xx miss → downstream executes, result persisted.
        IdempotencyResultStore store = mock(IdempotencyResultStore.class);
        when(store.find("k-new")).thenReturn(Optional.empty());
        IdempotencyFilter filter = new IdempotencyFilter(store, List.of());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/offers");
        request.addHeader("Idempotency-Key", "k-new");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain persistChain = (req, res) -> {
            res.setContentType("application/json");
            res.getOutputStream().write("{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
        };
        filter.doFilter(request, response, persistChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("{\"ok\":true}");
        verify(store).save(eq("k-new"), eq(200), eq("application/json"), any(byte[].class));

        // 5xx miss → downstream executes, nothing persisted.
        IdempotencyResultStore errorStore = mock(IdempotencyResultStore.class);
        when(errorStore.find("k-err")).thenReturn(Optional.empty());
        IdempotencyFilter errorFilter = new IdempotencyFilter(errorStore, List.of());

        MockHttpServletRequest errorRequest = new MockHttpServletRequest("POST", "/api/v1/offers");
        errorRequest.addHeader("Idempotency-Key", "k-err");
        MockHttpServletResponse errorResponse = new MockHttpServletResponse();
        FilterChain errorChain = (req, res) -> {
            ((jakarta.servlet.http.HttpServletResponse) res).setStatus(500);
            res.getOutputStream().write("boom".getBytes(StandardCharsets.UTF_8));
        };
        errorFilter.doFilter(errorRequest, errorResponse, errorChain);

        assertThat(errorResponse.getStatus()).isEqualTo(500);
        verify(errorStore, never()).save(any(), any(int.class), any(), any());
    }
}
