package com.essalud.samec.shared.correo;

import java.util.Map;

/**
 * Evento de correo que consume email-service (tópico samec-email-topic). Los nombres de campo
 * deben coincidir con email/.../model/EmailRequest: to, subject, templateName, variables.
 *
 * @param templateName nombre de la plantilla Thymeleaf en email/src/main/resources/templates
 */
public record CorreoRequest(String to, String subject, String templateName, Map<String, Object> variables) {
}
