package com.pullhire.notification.mail;

import com.pullhire.notification.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * Minimal text-template renderer for S1. Only the {@code verify_email}
 * template is defined; later slices add more codes. The verification link is
 * {@code ${APP_BASE_URL}/verify-email?token=<raw token>}.
 */
@Component
public class TemplateRenderer {

    public static final String VERIFY_EMAIL = "verify_email";

    private final AppProperties appProperties;

    public TemplateRenderer(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public RenderedEmail render(String templateCode, String recipientEmail, String verificationToken) {
        return switch (templateCode) {
            case VERIFY_EMAIL -> renderVerifyEmail(verificationToken);
            default -> throw new IllegalArgumentException("Неизвестный шаблон письма: " + templateCode);
        };
    }

    private RenderedEmail renderVerifyEmail(String token) {
        String link = appProperties.getBaseUrl() + "/verify-email?token=" + token;
        String subject = "PullHire — подтвердите адрес электронной почты";
        String body = "Здравствуйте!\n\n"
                + "Чтобы подтвердить адрес электронной почты, перейдите по ссылке:\n"
                + link + "\n\n"
                + "Ссылка одноразовая. Если вы не регистрировались, просто проигнорируйте это письмо.\n";
        return new RenderedEmail(subject, body);
    }

    /** Rendered subject + plain-text body. */
    public record RenderedEmail(String subject, String body) {
    }
}
