package me.yuugao.pullhire.outreach.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * outreach-service settings bound from top-level properties:
 * {@code app.selection.url} (category check against selection-service's
 * {@code GET /internal/candidates/{id}/category}),
 * {@code app.candidate.url} (internal identity mapping {@code sub → profile id})
 * and {@code auth.jwks-url} (local RS256 verification source,
 * {@code docs/architecture.md §6.1}).
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

        private final Selection selection = new Selection();
        private final Candidate candidate = new Candidate();

        public Selection getSelection() {
            return selection;
        }

        public Candidate getCandidate() {
            return candidate;
        }
    }

    /**
     * {@code app.selection.*} settings: base URL of selection-service's internal
     * API used for the invitation category check.
     */
    public static class Selection {

        private String url = "http://selection-service:8080";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    /**
     * {@code app.candidate.*} settings: base URL of candidate-service's internal
     * API used to resolve the JWT {@code sub} to a logical candidate id.
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
