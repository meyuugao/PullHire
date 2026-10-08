package me.yuugao.pullhire.candidate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * candidate-service settings: public SPA base URL, the resume file volume and the
 * upload cap ({@code docs/deployment.md §4}, {@code ${CANDIDATE_FILES_DIR}},
 * {@code ${CANDIDATE_RESUME_MAX_BYTES}}, OQ-15).
 */
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String baseUrl = "http://localhost:3000";
    private String filesDir = "/app/files";
    private final Resume resume = new Resume();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getFilesDir() {
        return filesDir;
    }

    public void setFilesDir(String filesDir) {
        this.filesDir = filesDir;
    }

    public Resume getResume() {
        return resume;
    }

    /** Resume upload settings ({@code app.resume.*}). */
    public static class Resume {

        private long maxBytes = 5_242_880L;

        public long getMaxBytes() {
            return maxBytes;
        }

        public void setMaxBytes(long maxBytes) {
            this.maxBytes = maxBytes;
        }
    }
}
