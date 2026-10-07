package com.pullhire.notification.mail;

import com.pullhire.notification.config.MailProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Thin adapter over {@link JavaMailSender} ({@code docs/data_model.md §10.1}).
 * Uses the configured envelope sender ({@code SMTP_FROM}); the dev target is Mailpit.
 */
@Component
public class MailSenderAdapter {

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    public MailSenderAdapter(JavaMailSender mailSender, MailProperties mailProperties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
    }

    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.getFrom());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
