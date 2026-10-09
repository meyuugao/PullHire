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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sync category check against selection-service's network-trusted
 * {@code GET /internal/candidates/{id}/category}.
 *
 * <p>{@code 200} with a category DTO means the candidate has a category;
 * {@code 404} means no category. Any other status or transport failure is a
 * technical error ({@link IllegalStateException}), not a business answer.
 */
@Component
public class CategoryCheckClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final JsonMapper jsonMapper = new JsonMapper();

    @Autowired
    public CategoryCheckClient(AppProperties properties) {
        this(properties.getApp().getSelection().getUrl());
    }

    /** Test constructor with an explicit selection-service base URL. */
    CategoryCheckClient(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        this.baseUrl = normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    /** {@code true} when selection-service reports a category for the candidate. */
    public boolean hasCategory(long candidateId) {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(baseUrl + "/internal/candidates/" + candidateId + "/category"))
                .timeout(TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 404) {
                return false;
            }
            if (response.statusCode() != 200) {
                throw new IllegalStateException("selection-service ответил HTTP " + response.statusCode());
            }
            return hasCategoryCode(response.body());
        } catch (IOException ex) {
            throw new IllegalStateException("selection-service недоступен: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание selection-service прервано", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean hasCategoryCode(String body) {
        Map<String, Object> payload = jsonMapper.readValue(body, Map.class);
        Object code = payload.get("category_code");
        if (code == null) {
            code = payload.get("categoryCode");
        }
        return code instanceof String text && !text.isBlank();
    }
}
