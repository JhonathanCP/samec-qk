package com.essalud.email.service;

import com.essalud.email.model.EmailRequest;
import jakarta.mail.MessagingException;

public interface EmailSenderService {
    void sendEmail(EmailRequest request) throws MessagingException;
}
