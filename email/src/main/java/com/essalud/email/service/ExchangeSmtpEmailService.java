package com.essalud.email.service;

import com.essalud.email.model.EmailRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.angus.mail.smtp.SMTPTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Envia correos via el relay SMTP interno de Essalud (Exchange, mail.essalud.gob.pe).
 * Estrategia por defecto: no requiere autenticacion (relay anonimo autorizado por IP),
 * pero el correo nunca aparecera en "Enviados" del remitente porque no hay sesion real
 * de ese buzon (ver GmailApiEmailService para esa capacidad).
 */
@Service
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "exchange", matchIfMissing = true)
public class ExchangeSmtpEmailService implements EmailSenderService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeSmtpEmailService.class);

    @Value("${app.mail.from}")
    private String fromAddress;

    @Autowired
    private JavaMailSenderImpl mailSender;

    @Autowired
    private TemplateEngine templateEngine;

    @Override
    public void sendEmail(EmailRequest request) throws MessagingException {
        Context context = new Context();
        context.setVariables(request.getVariables());

        String htmlContent = templateEngine.process(request.getTemplateName(), context);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setTo(request.getTo());
        helper.setSubject(request.getSubject());
        helper.setText(htmlContent, true);
        helper.setFrom(fromAddress);
        helper.addInline("logo_essalud_white.png", new ClassPathResource("assets/logo_essalud_white.png"));
        message.saveChanges();

        String messageId = message.getMessageID();
        log.info("[Exchange] Enviando correo a {} (asunto: {}, messageId: {})", request.getTo(), request.getSubject(), messageId);

        Transport transport = mailSender.getSession().getTransport("smtp");
        try {
            transport.connect(mailSender.getHost(), mailSender.getPort(), null, null);
            transport.sendMessage(message, message.getAllRecipients());

            String rawResponse = (transport instanceof SMTPTransport smtpTransport)
                    ? smtpTransport.getLastServerResponse()
                    : "(transporte no expone respuesta cruda)";
            log.info("[Exchange] Correo aceptado por el servidor SMTP para {} | messageId={} | respuesta del servidor: {}",
                    request.getTo(), messageId, rawResponse);
        } catch (SendFailedException e) {
            log.error("[Exchange] El servidor SMTP rechazo destinatarios para messageId={}: validos={}, invalidos={}, no enviados={}",
                    messageId, e.getValidSentAddresses(), e.getInvalidAddresses(), e.getValidUnsentAddresses(), e);
            throw e;
        } finally {
            transport.close();
        }
    }
}
