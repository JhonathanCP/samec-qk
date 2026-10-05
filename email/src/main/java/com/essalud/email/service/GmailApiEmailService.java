package com.essalud.email.service;

import com.essalud.email.model.EmailRequest;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Properties;

/**
 * Envia correos autenticado como un buzon real de Google Workspace, usando la API de
 * Gmail (HTTPS, no SMTP) con OAuth2 (client id/secret + refresh token de larga duracion).
 * A diferencia del relay de Exchange, este envio SI queda registrado en "Enviados" del
 * remitente, porque Gmail API actua exactamente como el propio cliente de Gmail.
 *
 * Requisitos previos (procedimiento manual, una sola vez):
 * 1. Crear un OAuth Client ID tipo "Desktop app" en Google Cloud Console (APIs & Services
 *    > Credentials), habilitando la API de Gmail en ese proyecto.
 * 2. Autorizar la aplicacion una vez iniciando sesion con el buzon remitente
 *    en el flujo de consentimiento OAuth (ver README para el paso a paso), obteniendo
 *    un refresh_token de larga duracion.
 * 3. Configurar GMAIL_OAUTH_CLIENT_ID, GMAIL_OAUTH_CLIENT_SECRET y
 *    GMAIL_OAUTH_REFRESH_TOKEN en el entorno.
 *
 * Activacion: variable de entorno MAIL_PROVIDER=gmail-api (por defecto usa Exchange).
 */
@Service
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "gmail-api")
public class GmailApiEmailService implements EmailSenderService {

    private static final Logger log = LoggerFactory.getLogger(GmailApiEmailService.class);

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.gmail.oauth-client-id}")
    private String clientId;

    @Value("${app.mail.gmail.oauth-client-secret}")
    private String clientSecret;

    @Value("${app.mail.gmail.oauth-refresh-token}")
    private String refreshToken;

    @Value("${app.mail.gmail.application-name:EsSalud SAMEC Email Service}")
    private String applicationName;

    private final TemplateEngine templateEngine;

    /**
     * volatile + inicialización perezosa con reintento: si el primer intento falla por algo
     * transitorio (p.ej. la red del contenedor aún no está lista al arrancar, o un corte breve
     * de conectividad hacia Google), NO se queda en null para siempre — cada envío vuelve a
     * intentar construir el cliente mientras siga sin lograrlo. Antes esto se hacía una sola
     * vez en @PostConstruct: si esa única vez fallaba, todos los envíos siguientes fallaban en
     * silencio (atrapados por el catch-all del listener de Kafka) hasta reiniciar el contenedor
     * — que era exactamente lo único que "arreglaba" el problema, porque @PostConstruct volvía
     * a correr en el arranque nuevo.
     */
    private volatile Gmail gmailClient;

    public GmailApiEmailService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /** Intento de "precalentado" al arrancar: si falla, no es fatal — sendEmail() reintenta solo. */
    @PostConstruct
    void initGmailClient() {
        try {
            obtenerClienteGmail();
        } catch (GeneralSecurityException | IOException e) {
            log.warn("[GmailAPI] No se pudo inicializar el cliente de Gmail API al arrancar (se " +
                    "reintentará automáticamente en el próximo envío). Verifique GMAIL_OAUTH_CLIENT_ID, " +
                    "GMAIL_OAUTH_CLIENT_SECRET y GMAIL_OAUTH_REFRESH_TOKEN si el problema persiste.", e);
        }
    }

    private synchronized Gmail obtenerClienteGmail() throws GeneralSecurityException, IOException {
        if (gmailClient != null) {
            return gmailClient;
        }
        UserCredentials credentials = UserCredentials.newBuilder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRefreshToken(refreshToken)
                .build();

        HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

        Gmail cliente = new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                requestInitializer)
                .setApplicationName(applicationName)
                .build();

        log.info("[GmailAPI] Cliente inicializado correctamente para el buzon {}", fromAddress);
        gmailClient = cliente;
        return cliente;
    }

    @Override
    public void sendEmail(EmailRequest request) throws MessagingException {
        Gmail cliente;
        try {
            cliente = obtenerClienteGmail();
        } catch (GeneralSecurityException | IOException e) {
            throw new MessagingException(
                    "No se pudo inicializar el cliente de Gmail API (se reintentará en el próximo envío). "
                            + "Revise las credenciales OAuth2 si el problema persiste.", e);
        }

        Context context = new Context();
        context.setVariables(request.getVariables());
        String htmlContent = templateEngine.process(request.getTemplateName(), context);

        Session session = Session.getDefaultInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
        helper.setTo(request.getTo());
        helper.setSubject(request.getSubject());
        helper.setText(htmlContent, true);
        helper.setFrom(fromAddress);
        helper.addInline("logo_essalud_white.png", new ClassPathResource("assets/logo_essalud_white.png"));
        mimeMessage.saveChanges();

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            mimeMessage.writeTo(buffer);
            String encodedEmail = Base64.getUrlEncoder().encodeToString(buffer.toByteArray());

            Message message = new Message();
            message.setRaw(encodedEmail);

            log.info("[GmailAPI] Enviando correo a {} (asunto: {}) como {}", request.getTo(), request.getSubject(), fromAddress);
            Message sent = cliente.users().messages().send("me", message).execute();
            log.info("[GmailAPI] Correo enviado y registrado en Enviados de {} | id={} | threadId={}",
                    fromAddress, sent.getId(), sent.getThreadId());
        } catch (IOException e) {
            throw new MessagingException("Error al enviar correo via Gmail API", e);
        }
    }
}
