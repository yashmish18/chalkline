package com.iet.dashboard.notifications.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;
    private final String from;

    public EmailService(@Autowired(required = false) JavaMailSender mailSender,
                        @Value("${app.mail.from:no-reply@jklu.edu.in}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Async
    public void sendEmailAsync(String to, String subject, String text) {
        if (mailSender == null) {
            log.info("[EMAIL MOCK] To: {}, Subject: {}, Body: {}", to, subject, text);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject("[Faculty Dashboard] " + subject);
            message.setText(text);
            mailSender.send(message);
            log.info("Notification email successfully dispatched to {}", to);
        } catch (Exception e) {
            log.warn("Failed to dispatch email to {}: {}. In-app notification preserved.", to, e.getMessage());
        }
    }
}
