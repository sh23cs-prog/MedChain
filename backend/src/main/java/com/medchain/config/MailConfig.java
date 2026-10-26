package com.medchain.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
@Slf4j
public class MailConfig {

    @Value("${spring.mail.host:${SMTP_HOST:${MAIL_HOST:smtp.gmail.com}}}")
    private String host;

    @Value("${spring.mail.port:${SMTP_PORT:${MAIL_PORT:587}}}")
    private int port;

    @Value("${spring.mail.username:${SMTP_USERNAME:${MAIL_USERNAME:}}}")
    private String username;

    @Value("${spring.mail.password:${SMTP_PASSWORD:${MAIL_PASSWORD:}}}")
    private String password;

    @Bean
    @Primary
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host != null && !host.isBlank() ? host.trim() : "smtp.gmail.com");
        mailSender.setPort(port > 0 ? port : 587);
        mailSender.setUsername(username != null ? username.trim() : "");
        mailSender.setPassword(password != null ? password.trim() : "");
        mailSender.setDefaultEncoding("UTF-8");

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");

        if (username != null && !username.isBlank()) {
            props.put("mail.smtp.auth", "true");
        }

        if (port == 465) {
            log.info("Configuring JavaMailSender with SSL on port 465 for host [{}]", mailSender.getHost());
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.trust", mailSender.getHost());
            props.put("mail.smtp.socketFactory.port", "465");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.fallback", "false");
        } else {
            log.info("Configuring JavaMailSender with STARTTLS capability on port [{}] for host [{}]", port, mailSender.getHost());
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.ssl.trust", mailSender.getHost());
        }

        return mailSender;
    }
}
