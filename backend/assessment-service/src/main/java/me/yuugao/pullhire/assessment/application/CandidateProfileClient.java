package me.yuugao.pullhire.assessment.application;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import me.yuugao.pullhire.assessment.error.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Resolves the JWT {@code sub} ({@code auth.users.id}) to the logical candidate id
 * ({@code candidate.profiles.id}) by calling candidate-service's network-trusted
 * {@code GET /internal/candidates/by-user/{userId}} — the same mapping
 * outreach-service applies ({@code docs/data_model.md §1}: every
 * {@code candidate_id} column is a logical reference to
 * {@code candidate.profiles.id}, never to {@code auth.users.id}).
 *
 * <p>A missing profile (404) means the caller is not a candidate — 403
 * {@code forbidden}. A transport/technical failure yields {@link IllegalStateException}.
 */
@Component
public class CandidateProfileClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final JsonMapper jsonMapper = new JsonMapper();

    @Autowired
    public CandidateProfileClient(@Value("${app.candidate.url:http://candidate-service:8080}") String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        this.baseUrl = normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    /** The caller's logical candidate id ({@code candidate.profiles.id}); 403 when the user has no profile. */
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
