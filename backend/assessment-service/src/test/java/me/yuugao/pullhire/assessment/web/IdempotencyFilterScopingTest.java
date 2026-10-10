package me.yuugao.pullhire.assessment.web;

import java.util.List;

import jakarta.servlet.FilterChain;
import me.yuugao.pullhire.idempotency.IdempotencyFilter;
import me.yuugao.pullhire.idempotency.IdempotencyResultStore;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Filter scoping for the answers-only idempotency wiring (docs/api.md §1,
 * decision B): {@code POST /assessment/survey} passes through key-less while
 * {@code POST /assessment/sessions/{id}/answers} without a key is rejected
 * with a 409 nested problem+json envelope. No Spring context, no DB.
 */
class IdempotencyFilterScopingTest {

    private static final List<String> ANSWER_PATTERN = List.of("/assessment/sessions/*/answers");

    @Test
    void surveyPassesThroughWithoutKey() throws Exception {
        IdempotencyResultStore store = mock(IdempotencyResultStore.class);
        IdempotencyFilter filter = new IdempotencyFilter(store, ANSWER_PATTERN);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/assessment/survey");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        verify(store, never()).find(any());
    }

    @Test
    void answersWithoutKeyReturns409NestedEnvelope() throws Exception {
        IdempotencyResultStore store = mock(IdempotencyResultStore.class);
        IdempotencyFilter filter = new IdempotencyFilter(store, ANSWER_PATTERN);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/assessment/sessions/1/answers");
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
}
