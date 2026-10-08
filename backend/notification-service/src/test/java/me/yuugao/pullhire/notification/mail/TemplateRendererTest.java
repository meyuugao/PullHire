package me.yuugao.pullhire.notification.mail;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the {@code verify_email} template ({@code docs/api.md §1}, OQ-13). */
class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void verifyEmailBodyEmbedsReadyVerificationUrlVerbatim() {
        String url = "https://app.pullhire.local/verify-email?token=one-time-abc";

        TemplateRenderer.RenderedEmail rendered =
                renderer.render("verify_email", "user@example.com", url);

        assertThat(rendered.subject()).isNotBlank();
        assertThat(rendered.body()).contains(url);
    }

    @Test
    void unknownTemplateIsRejected() {
        assertThatThrownBy(() -> renderer.render("unknown", "user@example.com",
                "https://app.pullhire.local/verify-email?token=x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
