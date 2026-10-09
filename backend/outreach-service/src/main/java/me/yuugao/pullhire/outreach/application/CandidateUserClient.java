package me.yuugao.pullhire.outreach.application;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import me.yuugao.pullhire.outreach.config.AppProperties;
import me.yuugao.pullhire.outreach.error.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Resolves the JWT {@code sub} ({@code auth.users.id}) to the logical candidate id
 * ({@code candidate.profiles.id}) by calling candidate-service's network-trusted
 * {@code GET /internal/candidates/by-user/{userId}}.
 *
 * <p>A missing profile (404) means the caller is not a candidate — 403
 * {@code forbidden}, not 404: the invitation itself exists, the caller just may
 * not see it. A transport/technical failure yields {@link IllegalStateException}.
 */
@Component
public class CandidateUserClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final JsonMapper jsonMapper = new JsonMapper();

    @Autowired
    public CandidateUserClient(AppProperties properties) {
        this(properties.getApp().getCandidate().getUrl());
    }

    /** Test constructor with an explicit candidate-service base URL. */
    CandidateUserClient(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        this.baseUrl = normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    /** The caller's candidate id; 403 when the user has no candidate profile. */
    public long resolveProfileId(long userId) {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(baseUrl + "/internal/candidates/by-user/" + userId))
                .timeout(TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 404) {
                throw ApiException.forbidden("JWT не является кандидатом");
            }
            if (response.statusCode() != 200) {
                throw new IllegalStateException("candidate-service ответил HTTP " + response.statusCode());
            }
            return candidateId(response.body());
        } catch (IOException ex) {
            throw new IllegalStateException("candidate-service недоступен: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание candidate-service прервано", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private long candidateId(String body) {
        Map<String, Object> payload = jsonMapper.readValue(body, Map.class);
        Object id = payload.get("candidateId");
        if (id == null) {
            id = payload.get("candidate_id");
        }
        if (id instanceof Number number) {
            return number.longValue();
        }
        if (id instanceof String text && !text.isBlank()) {
            return Long.parseLong(text.trim());
        }
        throw new IllegalStateException("Ответ candidate-service без candidateId");
    }
}
