package me.yuugao.pullhire.fsp.application;

import com.sun.net.httpserver.HttpServer;
import me.yuugao.pullhire.fsp.error.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CandidateProfileClient} resolves the JWT {@code sub} to a candidate id by
 * calling candidate-service's internal endpoint (docs/fsp_integration.md §2).
 * Served by a throwaway JDK {@link HttpServer}: no Spring context, no new dependency.
 */
class CandidateProfileClientTest {

    private HttpServer server;
    private CandidateProfileClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        client = new CandidateProfileClient("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void resolvesNumericCandidateId() {
        respond("/internal/candidates/by-user/7", 200, "{\"candidateId\":3}");

        assertThat(client.resolveProfileId(7L)).isEqualTo(3L);
    }

    @Test
    void resolvesStringCandidateId() {
        respond("/internal/candidates/by-user/7", 200, "{\"candidateId\":\"3\"}");

        assertThat(client.resolveProfileId(7L)).isEqualTo(3L);
    }

    @Test
    void missingProfileMapsTo404() {
        respond("/internal/candidates/by-user/7", 404, "{\"error\":{\"code\":\"not_found\"}}");

        assertThatThrownBy(() -> client.resolveProfileId(7L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).status().value()).isEqualTo(404));
    }

    private void respond(String path, int status, String body) {
        server.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }
}
