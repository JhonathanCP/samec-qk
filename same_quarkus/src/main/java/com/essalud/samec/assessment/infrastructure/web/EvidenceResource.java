package com.essalud.samec.assessment.infrastructure.web;

import java.util.List;

import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import com.essalud.samec.assessment.application.port.in.AssessmentViews.EvidenceView;
import com.essalud.samec.assessment.application.port.in.EvidenceUseCases;
import com.essalud.samec.assessment.application.port.in.EvidenceUseCases.ReviewEvidence;
import com.essalud.samec.assessment.domain.model.ReviewVerdict;
import com.essalud.samec.security.Permisos;

import io.quarkus.security.PermissionsAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;

/** One evidence submission: the evaluatee corrects it, the evaluator reviews it. */
@Path("/evidence")
public class EvidenceResource {

    @Inject
    EvidenceUseCases evidence;

    public record DetailsRequest(
            @Size(max = 1000, message = "Enlace: máximo 1000 caracteres") String externalUrl,
            @Size(max = 1000, message = "Notas: máximo 1000 caracteres") String notes) {
    }

    public record ReviewRequest(
            @NotNull(message = "Indique si aprueba u observa la evidencia") ReviewVerdict verdict,
            Long observationTypeId,
            @Size(max = 2000, message = "Comentario: máximo 2000 caracteres") String comment) {
    }

    @GET
    @Path("/{id}")
    @PermissionsAllowed(Permisos.AUTOEVALUACION_VER)
    public EvidenceView get(@PathParam("id") Long id) {
        return evidence.get(id);
    }

    // ── Evaluatee ──

    @POST
    @Path("/{id}/files")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @PermissionsAllowed(Permisos.EVIDENCIAS_REGISTRAR)
    public EvidenceView addFiles(@PathParam("id") Long id, @RestForm("files") List<FileUpload> files) {
        return evidence.addFiles(id, Uploads.toNewFiles(files));
    }

    @DELETE
    @Path("/{id}/files/{fileId}")
    @PermissionsAllowed(Permisos.EVIDENCIAS_REGISTRAR)
    public EvidenceView removeFile(@PathParam("id") Long id, @PathParam("fileId") Long fileId) {
        return evidence.removeFile(id, fileId);
    }

    @PATCH
    @Path("/{id}")
    @PermissionsAllowed(Permisos.EVIDENCIAS_REGISTRAR)
    public EvidenceView updateDetails(@PathParam("id") Long id, @Valid DetailsRequest request) {
        return evidence.updateDetails(id, request.externalUrl(), request.notes());
    }

    @POST
    @Path("/{id}/resubmit")
    @PermissionsAllowed(Permisos.EVIDENCIAS_REGISTRAR)
    public EvidenceView resubmit(@PathParam("id") Long id) {
        return evidence.resubmit(id);
    }

    @DELETE
    @Path("/{id}")
    @ResponseStatus(204)
    @PermissionsAllowed(Permisos.EVIDENCIAS_REGISTRAR)
    public void withdraw(@PathParam("id") Long id) {
        evidence.withdraw(id);
    }

    // ── Evaluator ──

    @POST
    @Path("/{id}/review")
    @PermissionsAllowed(Permisos.EVIDENCIAS_EVALUAR)
    public EvidenceView review(@PathParam("id") Long id, @Valid ReviewRequest request) {
        return evidence.review(id, new ReviewEvidence(request.verdict(), request.observationTypeId(), request.comment()));
    }
}
