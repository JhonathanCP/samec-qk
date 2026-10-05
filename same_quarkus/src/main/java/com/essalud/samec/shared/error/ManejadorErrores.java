package com.essalud.samec.shared.error;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import com.essalud.samec.shared.domain.DomainException;

import io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationException;
import io.quarkus.security.ForbiddenException;
import jakarta.persistence.PersistenceException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.UriInfo;

/**
 * Traduce las excepciones a respuestas JSON {@code { message, datetime, path }}. Los mensajes de
 * negocio se devuelven tal cual; una excepción no controlada se registra completa en el log, pero
 * al cliente solo le llega un mensaje genérico (no se exponen tablas, clases ni rutas internas).
 */
public class ManejadorErrores {

    private static final Logger LOG = Logger.getLogger(ManejadorErrores.class);

    /** Restricciones cuyo incumplimiento merece un mensaje propio (catálogo en uso por autoevaluaciones). */
    private static final Map<String, String> MENSAJES_RESTRICCION = Map.of(
            "fk_criterion_scores_criterion",
            "El criterio ya fue calificado en autoevaluaciones: no se puede eliminar.",
            "fk_evidence_submissions_audit_source",
            "Una de las fuentes auditables ya tiene sustentos cargados en autoevaluaciones: no se puede quitar ni eliminar.",
            "fk_condition_reviews_condition",
            "Una de las condiciones ya tiene verificaciones en campo registradas: no se puede quitar ni eliminar.",
            "fk_evidence_reviews_observation_type",
            "El tipo de observación ya se usó en revisiones: desactívelo en vez de eliminarlo.",
            "uk_criteria_code", "Ya existe un criterio con ese código.",
            "uk_standards_code", "Ya existe un estándar con ese código.",
            "uk_macro_processes_code", "Ya existe un macroproceso con ese código.",
            "uk_normative_documents_title", "Ya existe un documento normativo con ese título.",
            "uk_institutional_processes_parent_name", "Ya existe un proceso con ese nombre en el mismo nivel.",
            "uk_observation_types_code", "Ya existe un tipo de observación con ese código.");

    static final String MENSAJE_INTERNO =
            "Ha ocurrido un error interno. Intente nuevamente o contacte al administrador del sistema.";

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> reglaNegocio(ReglaNegocioException e, UriInfo uri) {
        return respuesta(RestResponse.Status.BAD_REQUEST, e.getMessage(), uri);
    }

    /** Regla del dominio (núcleo hexagonal): mismo trato que ReglaNegocioException. */
    @ServerExceptionMapper
    public RestResponse<ErrorResponse> reglaDominio(DomainException e, UriInfo uri) {
        return respuesta(RestResponse.Status.BAD_REQUEST, e.getMessage(), uri);
    }

    /**
     * Restricción de la BD incumplida (clave única, foránea o check). Se responde 409 con un
     * mensaje según el prefijo de la restricción (uk_, fk_, ck_), sin exponer tablas ni columnas.
     */
    @ServerExceptionMapper
    public RestResponse<ErrorResponse> persistencia(PersistenceException e, UriInfo uri) {
        Throwable t = e;
        while (t != null && !(t instanceof org.hibernate.exception.ConstraintViolationException)) t = t.getCause();
        if (t == null) return noControlada(e, uri);
        String restriccion = String.valueOf(((org.hibernate.exception.ConstraintViolationException) t).getConstraintName())
                .toLowerCase(Locale.ROOT);
        String mensaje = MENSAJES_RESTRICCION.entrySet().stream()
                .filter(m -> restriccion.contains(m.getKey())).map(Map.Entry::getValue).findFirst().orElse(null);
        if (mensaje != null) {
            LOG.debugf("Restricción %s incumplida en %s", restriccion, uri.getPath());
            return respuesta(RestResponse.Status.CONFLICT, mensaje, uri);
        }
        if (restriccion.contains("uk_")) {
            mensaje = "Ya existe un registro con esos datos.";
        } else if (restriccion.contains("fk_")) {
            mensaje = "No se puede completar la operación: el registro está relacionado con otros datos.";
        } else {
            mensaje = "Los datos no cumplen las reglas del sistema.";
        }
        LOG.debugf("Restricción %s incumplida en %s", restriccion, uri.getPath());
        return respuesta(RestResponse.Status.CONFLICT, mensaje, uri);
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> noEncontrado(RecursoNoEncontradoException e, UriInfo uri) {
        return respuesta(RestResponse.Status.NOT_FOUND, e.getMessage(), uri);
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> accesoDenegado(AccesoDenegadoException e, UriInfo uri) {
        return respuesta(RestResponse.Status.FORBIDDEN, e.getMessage(), uri);
    }

    /** Sin el permiso o rol que exige {@code @PermissionsAllowed} / {@code @RolesAllowed}. */
    @ServerExceptionMapper
    public RestResponse<ErrorResponse> prohibido(ForbiddenException e, UriInfo uri) {
        return respuesta(RestResponse.Status.FORBIDDEN, "No tiene permisos para realizar esta acción.", uri);
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> validacionEndpoint(ResteasyReactiveViolationException e, UriInfo uri) {
        return validacion(e, uri);
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> validacion(ConstraintViolationException e, UriInfo uri) {
        String mensaje = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .distinct()
                .collect(Collectors.joining(" "));
        return respuesta(RestResponse.Status.BAD_REQUEST, mensaje.isBlank() ? "Datos inválidos." : mensaje, uri);
    }

    /** Errores HTTP que genera el propio framework (404 de ruta, 405, JSON mal formado, …). */
    @ServerExceptionMapper
    public RestResponse<ErrorResponse> http(WebApplicationException e, UriInfo uri) {
        int status = e.getResponse().getStatus();
        if (status >= 500) {
            LOG.errorf(e, "Error HTTP %d en %s", status, uri.getPath());
            return respuesta(status, MENSAJE_INTERNO, uri);
        }
        String mensaje = switch (status) {
            case 400 -> "La solicitud no tiene un formato válido.";
            case 404 -> "Recurso no encontrado.";
            case 405 -> "Método no permitido.";
            case 415 -> "Tipo de contenido no soportado.";
            default -> e.getResponse().getStatusInfo().getReasonPhrase();
        };
        return respuesta(status, mensaje, uri);
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> noControlada(Exception e, UriInfo uri) {
        LOG.errorf(e, "Excepción no controlada en %s", uri.getPath());
        return respuesta(RestResponse.Status.INTERNAL_SERVER_ERROR, MENSAJE_INTERNO, uri);
    }

    private static RestResponse<ErrorResponse> respuesta(RestResponse.Status status, String mensaje, UriInfo uri) {
        return respuesta(status.getStatusCode(), mensaje, uri);
    }

    private static RestResponse<ErrorResponse> respuesta(int status, String mensaje, UriInfo uri) {
        return RestResponse.ResponseBuilder.<ErrorResponse>create(status)
                .entity(ErrorResponse.de(mensaje, uri.getPath()))
                .build();
    }
}
