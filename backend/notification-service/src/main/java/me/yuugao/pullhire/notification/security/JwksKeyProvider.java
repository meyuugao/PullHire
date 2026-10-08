package me.yuugao.pullhire.notification.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Obtains auth-service's RS256 public key for local token validation
 * ({@code docs/architecture.md §6.1}). Primary source is the JWKS endpoint
 * ({@code ${AUTH_JWKS_URL}}) with a short-lived in-memory cache; if the fetch
 * fails it falls back to the static {@code ${JWT_PUBLIC_KEY}} when configured.
 */
@Component
public class JwksKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwksKeyProvider.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(3);

    private final String jwksUrl;
    private final RSAPublicKey staticFallback;
    private final HttpClient httpClient;
    private final JsonMapper jsonMapper = new JsonMapper();

    private volatile RSAPublicKey cached;
    private volatile Instant cachedAt = Instant.EPOCH;

    @Autowired
    public JwksKeyProvider(Environment environment) {
        this(environment.getProperty("auth.jwks-url", "http://auth-service:8080/.well-known/jwks.json"),
                parseStaticKey(environment.getProperty("JWT_PUBLIC_KEY")));
    }

    /** Test/fallback constructor: a null {@code jwksUrl} disables remote fetch. */
    JwksKeyProvider(String jwksUrl, RSAPublicKey staticFallback) {
        this.jwksUrl = jwksUrl;
        this.staticFallback = staticFallback;
        this.httpClient = HttpClient.newBuilder().connectTimeout(FETCH_TIMEOUT).build();
    }

    /** Returns the current verification key, refreshing the cache when stale. */
    public RSAPublicKey publicKey() {
        RSAPublicKey current = cached;
        if (current != null && Instant.now().isBefore(cachedAt.plus(CACHE_TTL))) {
            return current;
        }
        if (jwksUrl == null || jwksUrl.isBlank()) {
            return requireStatic();
        }
        try {
            RSAPublicKey fetched = fetch();
            cached = fetched;
            cachedAt = Instant.now();
            return fetched;
        } catch (Exception ex) {
            log.warn("JWKS недоступен ({}): {}", jwksUrl, ex.getMessage());
            return requireStatic();
        }
    }

    private RSAPublicKey requireStatic() {
        if (staticFallback != null) {
            return staticFallback;
        }
        throw new IllegalStateException("Публичный ключ JWT недоступен: JWKS не загружен и JWT_PUBLIC_KEY не задан");
    }

    @SuppressWarnings("unchecked")
    private RSAPublicKey fetch() throws IOException, InterruptedException, GeneralSecurityException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(jwksUrl))
                .timeout(FETCH_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("JWKS HTTP " + response.statusCode());
        }
        Map<String, Object> root = jsonMapper.readValue(response.body(), Map.class);
        Object keys = root.get("keys");
        if (!(keys instanceof List<?> list)) {
            throw new IOException("JWKS без массива keys[]");
        }
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> key && "RSA".equals(key.get("kty"))) {
                return toPublicKey(String.valueOf(key.get("n")), String.valueOf(key.get("e")));
            }
        }
        throw new IOException("JWKS без RSA-ключа");
    }

    private static RSAPublicKey toPublicKey(String modulusBase64, String exponentBase64)
            throws GeneralSecurityException {
        BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(modulusBase64));
        BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(exponentBase64));
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(modulus, exponent));
    }

    /** Parses {@code JWT_PUBLIC_KEY} as PEM or raw base64 X.509 SubjectPublicKeyInfo. */
    private static RSAPublicKey parseStaticKey(String value) {
        byte[] der = decode(value);
        if (der == null) {
            return null;
        }
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException ex) {
            log.warn("Не удалось разобрать JWT_PUBLIC_KEY: {}", ex.getMessage());
            return null;
        }
    }

    private static byte[] decode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replace("\\n", "\n").trim();
        if (normalized.contains("-----BEGIN")) {
            normalized = normalized
                    .replaceAll("-----BEGIN [^-]+-----", "")
                    .replaceAll("-----END [^-]+-----", "")
                    .replaceAll("\\s", "");
        } else {
            normalized = normalized.replaceAll("\\s", "");
        }
        if (normalized.isEmpty()) {
            return null;
        }
        for (Base64.Decoder decoder : List.of(Base64.getDecoder(), Base64.getMimeDecoder(), Base64.getUrlDecoder())) {
            try {
                return decoder.decode(normalized);
            } catch (IllegalArgumentException ignored) {
                // try the next alphabet
            }
        }
        return null;
    }
}
