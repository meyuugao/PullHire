package com.pullhire.notification.mail;

import com.pullhire.notification.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the {@code verify_email} template link ({@code docs/api.md §1}). */
class TemplateRendererTest {

    private TemplateRenderer renderer(String baseUrl) {
        AppProperties properties = new AppProperties();
        properties.setBaseUrl(baseUrl);
        return new TemplateRenderer(properties);
    }

    @Test
    void verifyEmailBodyEmbedsRawTokenInLink() {
        TemplateRenderer.RenderedEmail rendered =
                renderer("https://app.pullhire.local").render("verify_email", "user@example.com", "raw-token-abc");

        assertThat(rendered.subject()).isNotBlank();
        assertThat(rendered.body())
                .contains("https://app.pullhire.local/verify-email?token=raw-token-abc");
    }

    @Test
    void unknownTemplateIsRejected() {
        assertThatThrownBy(() -> renderer("http://localhost:3000").render("unknown", "user@example.com", "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
