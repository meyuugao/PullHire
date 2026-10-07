package com.pullhire.auth.security;

import com.pullhire.auth.application.TokenService;
import com.pullhire.auth.domain.AccessClaims;
import com.pullhire.auth.error.ApiException;
import com.pullhire.auth.web.ApiVersionFilter;
import com.pullhire.auth.web.ErrorEnvelope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Local RS256 bearer-token validation ({@code docs/architecture.md §6.1},
 * {@code docs/api.md §1}). Protected paths require a valid token; a missing,
 * expired or foreign token yields 401 {@code application/problem+json}
 * {@code unauthenticated}. Public auth/JWKS/health paths are exempt.
 */
@Component
@Order(2)
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String ATTR_USER_ID = "authUserId";
    public static final String ATTR_ROLE = "authRole";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/auth/register",
            "/auth/verify-email",
            "/auth/login",
            "/auth/refresh",
            "/.well-known/",
            "/healthz",
            "/readyz",
            "/metrics",
            "/v3/api-docs",
            "/swagger-ui",
            "/actuator",
            "/error");

    private final TokenService tokenService;
    private final JsonMapper jsonMapper;

    public JwtAuthFilter(TokenService tokenService, JsonMapper jsonMapper) {
        this.tokenService = tokenService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean publicPath = PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                AccessClaims claims = tokenService.verifyAccessToken(token);
                request.setAttribute(ATTR_USER_ID, claims.userId());
                request.setAttribute(ATTR_ROLE, claims.role());
            } catch (ApiException ex) {
                if (!publicPath) {
                    writeUnauthorized(response, request, ex);
                    return;
                }
            }
        } else if (!publicPath) {
            writeUnauthorized(response, request,
                    ApiException.unauthenticated("Требуется заголовок Authorization: Bearer <JWT>"));
            return;
        }

        chain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, HttpServletRequest request, ApiException ex)
            throws IOException {
        response.setStatus(ex.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ErrorEnvelope body = ErrorEnvelope.of(ex.code(), ex.getMessage(),
                request.getHeader(ApiVersionFilter.REQUEST_ID_HEADER));
        jsonMapper.writeValue(response.getWriter(), body);
    }
}
