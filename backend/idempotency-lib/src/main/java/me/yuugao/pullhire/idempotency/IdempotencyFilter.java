package me.yuugao.pullhire.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Idempotency-Key middleware for mutating POST requests (docs/api.md §1–§2).
 *
 * <ul>
 *   <li>Missing {@code Idempotency-Key} header → 409 problem+json envelope
 *       nested under {@code error} (api.md §2).</li>
 *   <li>Known key → replays the stored status + body + Content-Type without
 *       re-executing the effect.</li>
 *   <li>Unknown key → executes downstream once, persists 2xx results only;
 *       errors execute normally and are never cached.</li>
 * </ul>
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";

    /** MDC key populated by event-lib's CorrelationMdcFilter. */
    static final String CORRELATION_MDC_KEY = "correlation_id";

    private final IdempotencyResultStore store;
    private final List<String> pathPatterns;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public IdempotencyFilter(IdempotencyResultStore store, List<String> pathPatterns) {
        this.store = store;
        this.pathPatterns = pathPatterns != null ? List.copyOf(pathPatterns) : List.of();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (pathPatterns.isEmpty()) {
            return false;
        }
        String path = request.getRequestURI();
        return pathPatterns.stream().noneMatch(pattern -> matcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank()) {
            writeConflict(response, "Idempotency-Key header is required for this operation");
            return;
        }

        var hit = store.find(key);
        if (hit.isPresent()) {
            var stored = hit.get();
            if (stored.contentType() != null) {
                response.setContentType(stored.contentType());
            }
            byte[] body = stored.body() != null ? stored.body() : new byte[0];
            response.setStatus(stored.statusCode());
            response.setContentLength(body.length);
            response.getOutputStream().write(body);
            return;
        }

        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, wrapper);
        } finally {
            byte[] body = wrapper.getContentAsByteArray();
            int status = wrapper.getStatus();
            if (status >= 200 && status < 300) {
                String contentType = wrapper.getContentType();
                try {
                    store.save(key, status, contentType, body);
                } catch (org.springframework.dao.DuplicateKeyException e) {
                    // Parallel duplicate: first wins. Re-read the winner; if the
                    // row is not yet visible, signal a conflict so the client retries.
                    var winner = store.find(key);
                    if (winner.isEmpty()) {
                        wrapper.reset();
                        writeConflict(response, "Duplicate Idempotency-Key: concurrent request in progress");
                        return;
                    }
                }
            }
            wrapper.copyBodyToResponse();
        }
    }

    private static void writeConflict(HttpServletResponse response, String message) throws IOException {
        String requestId = MDC.get(CORRELATION_MDC_KEY);
        if (requestId == null) {
            requestId = "";
        }
        String timestamp = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toString();
        String payload = "{\"error\":{\"code\":\"conflict\",\"message\":\""
                + escape(message) + "\",\"request_id\":\""
                + escape(requestId) + "\",\"timestamp\":\"" + timestamp + "\"}}";
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
