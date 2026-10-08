package me.yuugao.pullhire.employer.security;

import me.yuugao.pullhire.employer.web.ApiVersionFilter;
import me.yuugao.pullhire.employer.web.ErrorEnvelope;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Local RS256 bearer-token validation ({@code docs/architecture.md §6.1},
 * {@code docs/api.md §1}). The public key comes from the cached JWKS, selected by
 * the token's {@code kid} header when present (F10); a missing, expired, foreign or
 * malformed token yields 401 {@code application/problem+json}
 * {@code unauthenticated}. Health/JWKS/docs paths and {@code /internal/} are
 * exempt (network trust; the gateway does not route {@code /internal}). Requests
 * under {@code /employer} require the {@code employer} role in any realm role
 * (F11), else 403 {@code forbidden}.
 */
@Component
@Order(2)
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String ATTR_USER_ID = "employerUserId";
    public static final String ATTR_ROLE = "employerRole";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ISSUER = "pullhire-auth";
    private static final String ROLES_CLAIM = "realm_access";
    private static final String ROLES_KEY = "roles";
    private static final String KID_HEADER = "kid";
    private static final String EMPLOYER_ROLE = "employer";
    private static final String EMPLOYER_PREFIX = "/employer";
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/healthz",
            "/readyz",
            "/metrics",
            "/docs",
            "/internal/",
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
                List<String> roles = rolesFromClaims(claims);
                if (path.startsWith(EMPLOYER_PREFIX) && !roles.contains(EMPLOYER_ROLE)) {
                    writeForbidden(response, request, "Требуется роль employer");
                    return;
                }
                request.setAttribute(ATTR_USER_ID, Long.parseLong(claims.getSubject()));
                request.setAttribute(ATTR_ROLE, roles.isEmpty() ? null : roles.get(0));
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
                .verifyWith(keys.publicKey(keyId(token)))
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }

    /** Reads the {@code kid} header of a compact JWS without verifying it. */
    @SuppressWarnings("unchecked")
    private String keyId(String token) {
        int dot = token.indexOf('.');
        if (dot <= 0) {
            return null;
        }
        try {
            String json = new String(Base64.getUrlDecoder().decode(token.substring(0, dot)), StandardCharsets.UTF_8);
            Map<String, Object> header = jsonMapper.readValue(json, Map.class);
            Object kid = header.get(KID_HEADER);
            return kid == null ? null : String.valueOf(kid);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static List<String> rolesFromClaims(Claims claims) {
        Object realm = claims.get(ROLES_CLAIM);
        if (realm instanceof Map<?, ?> map) {
            Object roles = map.get(ROLES_KEY);
            if (roles instanceof List<?> list) {
                List<String> result = new ArrayList<>();
                for (Object role : list) {
                    if (role != null) {
                        result.add(String.valueOf(role));
                    }
                }
                return result;
            }
        }
        return List.of();
    }

    private void writeUnauthorized(HttpServletResponse response, HttpServletRequest request, String message)
            throws IOException {
        writeProblem(response, request, HttpServletResponse.SC_UNAUTHORIZED, "unauthenticated", message);
    }

    private void writeForbidden(HttpServletResponse response, HttpServletRequest request, String message)
            throws IOException {
        writeProblem(response, request, HttpServletResponse.SC_FORBIDDEN, "forbidden", message);
    }

    private void writeProblem(HttpServletResponse response, HttpServletRequest request, int status,
                              String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ErrorEnvelope body = ErrorEnvelope.of(code, message, request.getHeader(ApiVersionFilter.REQUEST_ID_HEADER));
        jsonMapper.writeValue(response.getWriter(), body);
    }
}
