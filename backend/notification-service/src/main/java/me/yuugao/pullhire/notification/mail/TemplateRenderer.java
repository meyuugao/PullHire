package me.yuugao.pullhire.notification.mail;

import org.springframework.stereotype.Component;

/**
 * Minimal text-template renderer for S1/S2. Only the {@code verify_email}
 * template is defined; later slices add more codes.
 *
 * <p>OQ-13: the producer renders the full one-time link, so this renderer takes
 * the ready {@code verificationUrl} verbatim and never constructs a link from a
 * token. There is deliberately no {@code app.base-url} dependency here.
 */
@Component
public class TemplateRenderer {

    public static final String VERIFY_EMAIL = "verify_email";
    public static final String CONTACT_RELEASED = "contact_released";

    public RenderedEmail render(String templateCode, String recipientEmail, String verificationUrl) {
        return switch (templateCode) {
            case VERIFY_EMAIL -> renderVerifyEmail(verificationUrl);
            case CONTACT_RELEASED -> renderContactReleased();
            default -> throw new IllegalArgumentException("Неизвестный шаблон письма: " + templateCode);
        };
    }

    public RenderedEmail render(String templateCode, String recipientEmail) {
        return render(templateCode, recipientEmail, null);
    }

    private RenderedEmail renderVerifyEmail(String verificationUrl) {
        String subject = "PullHire — подтвердите адрес электронной почты";
        String body = "Здравствуйте!\n\n"
                + "Чтобы подтвердить адрес электронной почты, перейдите по ссылке:\n"
                + verificationUrl + "\n\n"
                + "Ссылка одноразовая. Если вы не регистрировались, просто проигнорируйте это письмо.\n";
        return new RenderedEmail(subject, body);
    }

    private RenderedEmail renderContactReleased() {
        String subject = "PullHire — контакты раскрыты";
        String body = "Здравствуйте!\n\n"
                + "Контакты раскрыты: работодателю открыты ваши контактные данные после принятия приглашения.\n";
        return new RenderedEmail(subject, body);
    }

    /** Rendered subject + plain-text body. */
    public record RenderedEmail(String subject, String body) {
    }
}
