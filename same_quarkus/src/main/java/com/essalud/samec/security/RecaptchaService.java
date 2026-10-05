package com.essalud.samec.security;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.jboss.logging.Logger;

import com.essalud.samec.shared.config.SamecConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Verificación de reCAPTCHA v3 (login, olvidé mi contraseña y autorregistro). Desactivado por
 * defecto: se activa con SAMEC_RECAPTCHA_ENABLED=true y SAMEC_RECAPTCHA_SECRET.
 */
@ApplicationScoped
public class RecaptchaService {

    private static final Logger LOG = Logger.getLogger(RecaptchaService.class);
    private static final URI VERIFICAR = URI.create("https://www.google.com/recaptcha/api/siteverify");

    // Con timeout: sin él, un login quedaba colgado si Google no respondía.
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Inject
    SamecConfig config;

    @Inject
    ObjectMapper json;

    public boolean habilitado() {
        return config.recaptcha().enabled();
    }

    /** true si reCAPTCHA está desactivado o el token es válido. */
    public boolean verificar(String token) {
        if (!habilitado()) return true;
        if (token == null || token.isBlank()) return false;
        String cuerpo = "secret=" + URLEncoder.encode(config.recaptcha().secret().orElse(""), StandardCharsets.UTF_8)
                + "&response=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        try {
            HttpResponse<String> respuesta = http.send(HttpRequest.newBuilder(VERIFICAR)
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                    .build(), HttpResponse.BodyHandlers.ofString());
            JsonNode r = json.readTree(respuesta.body());
            if (!r.path("success").asBoolean(false)) return false;
            // v3 devuelve un puntaje (0.0–1.0); v2 no lo incluye.
            return !r.has("score") || r.get("score").asDouble() >= config.recaptcha().puntajeMinimo();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            // Timeout o Google inalcanzable: se trata como inválido (400 normal), no como error 500.
            LOG.warnf("No se pudo verificar reCAPTCHA: %s", e.getMessage());
            return false;
        }
    }
}
