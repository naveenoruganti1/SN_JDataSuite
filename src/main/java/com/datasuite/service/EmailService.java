package com.datasuite.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

@Service
public class EmailService {

	@Value("${spring.mail.host}")
    private String host;

    @Value("${spring.mail.port}")
    private int port;

    @Value("${spring.mail.username}")
    private String username;

    @Value("${spring.mail.password}")
    private String password;
    
    @Value("${spring.mail.properties.mail.smtp.auth}")
    private String smtpAuth;
    
    @Value("${spring.mail.properties.mail.smtp.starttls.enable}")
    private String tlsEnable;
    
    @Value("${spring.mail.properties.mail.smtp.starttls.required}")
    private String tlsRequired;
    
    @Value("${spring.mail.properties.mail.smtp.ssl.protocols}")
    private String sslProtocl;
    
    public void sendEmail(String from, String to, String subject, String content) throws MessagingException {
    	Properties props = new Properties();
    	props.put("mail.smtp.auth", smtpAuth);
        props.put("mail.smtp.starttls.enable", tlsEnable);
        props.put("mail.smtp.starttls.required", tlsRequired);
        props.put("mail.smtp.ssl.protocols", sslProtocl);
        props.put("mail.smtp.ssl.trust", host);
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);

        Session session = Session.getInstance(props,
            new jakarta.mail.Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setText(content);

            Transport.send(message);
        } catch (Exception e) {
        	System.out.println("Error at sendEmail "+e.getMessage());
            throw new MessagingException("Failed to send email: " + e.getMessage(), e);
        }
    }
}