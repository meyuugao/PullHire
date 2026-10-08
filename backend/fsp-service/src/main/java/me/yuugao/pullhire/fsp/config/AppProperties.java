package me.yuugao.pullhire.fsp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * fsp-service settings bound from top-level properties:
 * {@code app.fsp.adapter} (registry seam selection, {@code docs/fsp_integration.md §1}),
 * {@code auth.jwks-url} (local RS256 verification source,
 * {@code docs/architecture.md §6.1}) and {@code app.candidate.url} (internal
 * identity mapping {@code sub → profile id}, {@code docs/fsp_integration.md §2}).
 */
@Component
@ConfigurationProperties
public class AppProperties {

    private final App app = new App();
    private final Auth auth = new Auth();

    public App getApp() {
        return app;
    }

    public Auth getAuth() {
        return auth;
    }

    /** {@code app.*} settings. */
    public static class App {

        private final Fsp fsp = new Fsp();
        private final Candidate candidate = new Candidate();

        public Fsp getFsp() {
            return fsp;
        }

        public Candidate getCandidate() {
            return candidate;
        }
    }

    /** {@code app.fsp.*} settings. */
    public static class Fsp {

        private String adapter = "mock";

        public String getAdapter() {
            return adapter;
        }

        public void setAdapter(String adapter) {
            this.adapter = adapter;
        }
    }

    /**
     * {@code app.candidate.*} settings: base URL of candidate-service's internal
     * API used to resolve the JWT {@code sub} to a logical candidate id
     * ({@code docs/fsp_integration.md §2}).
     */
    public static class Candidate {

        private String url = "http://candidate-service:8080";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    /** {@code auth.*} settings. */
    public static class Auth {

        private String jwksUrl = "http://auth-service:8080/.well-known/jwks.json";

        public String getJwksUrl() {
            return jwksUrl;
        }

        public void setJwksUrl(String jwksUrl) {
            this.jwksUrl = jwksUrl;
        }
    }
}
