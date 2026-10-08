package me.yuugao.pullhire.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * JWT TTLs ({@code docs/architecture.md §6.1}). Defaults follow the S1 decision:
 * short-lived access token (15 min) and rotatable refresh token (30 days).
 */
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private Duration accessTtl = Duration.ofMinutes(15);
    private Duration refreshTtl = Duration.ofDays(30);

    public Duration getAccessTtl() {
        return accessTtl;
    }

    public void setAccessTtl(Duration accessTtl) {
        this.accessTtl = accessTtl;
    }

    public Duration getRefreshTtl() {
        return refreshTtl;
    }

    public void setRefreshTtl(Duration refreshTtl) {
        this.refreshTtl = refreshTtl;
    }
}
