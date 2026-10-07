package com.pullhire.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Sender identity for outbound mail ({@code docs/data_model.md §10.1}).
 * The SMTP host/port themselves are bound by Spring Boot's own mail properties.
 */
@Component
@ConfigurationProperties(prefix = "spring.mail")
public class MailProperties {

    private String from = "no-reply@pullhire.local";

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }
}
