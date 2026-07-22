package com.delivera.data.email.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String from;

    public EmailService(
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${app.mail.from:noreply@delivera.app}") String from) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
    }

    public void sendTrackingLink(String recipientEmail, String recipientName, String reference, String trackingUrl) {
        if (!enabled) {
            log.info("Mail disabled — would send tracking link to {} for order {}", recipientEmail, reference);
            return;
        }
        if (mailSender == null) {
            log.warn("Mail enabled but JavaMailSender not configured — skipping email to {} for order {}", recipientEmail, reference);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(recipientEmail);
            msg.setSubject("Tu pedido " + reference + " está en camino");
            msg.setText(
                "Hola " + (recipientName != null ? recipientName : "") + ",\n\n" +
                "Puedes seguir el estado de tu pedido (" + reference + ") en el siguiente enlace:\n\n" +
                trackingUrl + "\n\n" +
                "— Delivera"
            );
            mailSender.send(msg);
            log.info("Tracking email sent to {} for order {}", recipientEmail, reference);
        } catch (Exception e) {
            log.warn("Failed to send tracking email to {}: {}", recipientEmail, e.getMessage());
        }
    }
}
