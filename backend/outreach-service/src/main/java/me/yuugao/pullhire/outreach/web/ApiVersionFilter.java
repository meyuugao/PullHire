package me.yuugao.pullhire.outreach.web;

import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Версионирование API ({@code docs/api.md §1}): контракт версии 1 передаётся
 * заголовком {@code X-Api-Version} (по умолчанию 1). Запрос с иной версией
 * отклоняется тем же problem+json-конвертом.
 */
@Component
@Order(1)
public class ApiVersionFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Api-Version";
    public static final String CURRENT_VERSION = "1";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final JsonMapper objectMapper;

    public ApiVersionFilter(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String version = request.getHeader(HEADER);
        response.setHeader(HEADER, CURRENT_VERSION);

        if (version != null && !version.isBlank() && !CURRENT_VERSION.equals(version.trim())) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            ErrorEnvelope body = ErrorEnvelope.of(
                    "validation_error",
                    "Неподдерживаемая версия API: " + version,
                    List.of(new ErrorEnvelope.Detail(HEADER, "unsupported_version")),
                    requestId(request));
            objectMapper.writeValue(response.getWriter(), body);
            return;
        }

        chain.doFilter(request, response);
    }

    private static String requestId(HttpServletRequest request) {
        String id = request.getHeader(REQUEST_ID_HEADER);
        return id == null ? "" : id;
    }
}
