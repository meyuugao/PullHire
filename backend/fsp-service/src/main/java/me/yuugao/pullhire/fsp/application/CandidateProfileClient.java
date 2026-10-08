package me.yuugao.pullhire.fsp.application;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import me.yuugao.pullhire.fsp.config.AppProperties;
import me.yuugao.pullhire.fsp.error.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Resolves the JWT {@code sub} ({@code auth.users.id}) to the logical candidate id
 * ({@code candidate.profiles.id}) by calling candidate-service's network-trusted
 * {@code GET /internal/candidates/by-user/{userId}} ({@code docs/fsp_integration.md §2}).
 *
 * <p>The candidate id is the stable logical reference used everywhere downstream —
 * {@code fsp_links.candidate_id}, both event payloads and
 * {@code /internal/fsp/{candidateId}} — so the two services agree on one id space
 * ({@code docs/data_model.md §5.1}). A missing profile yields 404 {@code not_found};
 * a transport/technical failure yields 500 {@code internal_error}.
 */
@Component
public class CandidateProfileClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final JsonMapper jsonMapper = new JsonMapper();

    @Autowired
    public CandidateProfileClient(AppProperties properties) {
        this(properties.getApp().getCandidate().getUrl());
    }

    /** Test constructor with an explicit candidate-service base URL. */
    CandidateProfileClient(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        this.baseUrl = normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    /** The caller's candidate id; 404 when the user has no candidate profile yet. */
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
                throw ApiException.notFound("Профиль кандидата не найден");
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
        if (id instanceof Number number) {
            return number.longValue();
        }
        if (id instanceof String text && !text.isBlank()) {
            return Long.parseLong(text.trim());
        }
        throw new IllegalStateException("Ответ candidate-service без candidateId");
    }
}
