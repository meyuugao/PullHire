package me.yuugao.pullhire.notification.security;

import me.yuugao.pullhire.notification.web.ApiVersionFilter;
import me.yuugao.pullhire.notification.web.ErrorEnvelope;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
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
import java.util.Map;

/**
 * Local RS256 bearer-token validation ({@code docs/architecture.md §6.1},
 * {@code docs/api.md §1}). The public key comes from the cached JWKS; a missing,
 * expired, foreign or malformed token yields 401 {@code application/problem+json}
 * {@code unauthenticated}. Health/JWKS/docs paths are exempt.
 */
@Component
@Order(2)
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String ATTR_USER_ID = "authUserId";
    public static final String ATTR_ROLE = "authRole";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ISSUER = "pullhire-auth";
    private static final String ROLES_CLAIM = "realm_access";
    private static final String ROLES_KEY = "roles";
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/healthz",
            "/readyz",
            "/metrics",
            "/v3/api-docs",
            "/swagger-ui",
            "/.well-known/");

    private final JwksKeyProvider keys;
    private final JsonMapper jsonMapper;

    public JwtAuthFilter(JwksKeyProvider keys, JsonMapper jsonMapper) {
        this.keys = keys;
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
                Claims claims = verify(token);
                request.setAttribute(ATTR_USER_ID, Long.parseLong(claims.getSubject()));
                request.setAttribute(ATTR_ROLE, roleFromClaims(claims));
            } catch (JwtException | IllegalArgumentException | IllegalStateException ex) {
                if (!publicPath) {
                    writeUnauthorized(response, request, "Невалидный или истёкший токен");
                    return;
                }
            }
        } else if (!publicPath) {
            writeUnauthorized(response, request, "Требуется заголовок Authorization: Bearer <JWT>");
            return;
        }

        chain.doFilter(request, response);
    }

    private Claims verify(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(keys.publicKey())
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }

    private static String roleFromClaims(Claims claims) {
        Object realm = claims.get(ROLES_CLAIM);
        if (realm instanceof Map<?, ?> map) {
            Object roles = map.get(ROLES_KEY);
            if (roles instanceof List<?> list && !list.isEmpty()) {
                return String.valueOf(list.get(0));
            }
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response, HttpServletRequest request, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ErrorEnvelope body = ErrorEnvelope.of("unauthenticated", message,
                request.getHeader(ApiVersionFilter.REQUEST_ID_HEADER));
        jsonMapper.writeValue(response.getWriter(), body);
    }
}
