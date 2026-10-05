package com.essalud.samec.assessment.infrastructure.web;

import java.util.List;

import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import com.essalud.samec.assessment.application.port.in.AssessmentViews.ChecklistView;
import com.essalud.samec.assessment.application.port.in.AssessmentViews.ConditionReviewView;
import com.essalud.samec.assessment.application.port.in.AssessmentViews.CriterionScoreView;
import com.essalud.samec.assessment.application.port.in.AssessmentViews.EvidenceView;
import com.essalud.samec.assessment.application.port.in.AssessmentViews.ResultView;
import com.essalud.samec.assessment.application.port.in.AssessmentViews.SelfAssessmentView;
import com.essalud.samec.assessment.application.port.in.EvaluationUseCases;
import com.essalud.samec.assessment.application.port.in.EvaluationUseCases.ReviewCondition;
import com.essalud.samec.assessment.application.port.in.EvaluationUseCases.ScoreCriterion;
import com.essalud.samec.assessment.application.port.in.EvidenceUseCases;
import com.essalud.samec.assessment.application.port.in.EvidenceUseCases.SubmitEvidence;
import com.essalud.samec.assessment.application.port.in.SelfAssessmentUseCases;
import com.essalud.samec.assessment.application.port.in.SelfAssessmentUseCases.StartSelfAssessment;
import com.essalud.samec.assessment.domain.model.AssessmentStatus;
import com.essalud.samec.assessment.domain.model.SubmissionStatus;
import com.essalud.samec.catalog.domain.model.FacilityCategory;
import com.essalud.samec.security.Permisos;
import com.essalud.samec.shared.domain.DomainException;
import com.essalud.samec.shared.domain.Page;
import com.essalud.samec.shared.domain.PageRequest;

import io.quarkus.security.PermissionsAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Self-assessments of the facilities. Every endpoint needs a valid access token and a permission;
 * the facility scope (an IPRESS user only sees their own IPRESS) is checked by the use cases.
 */
@Path("/self-assessments")
public class SelfAssessmentResource {

    @Inject SelfAssessmentUseCases assessments;
    @Inject EvidenceUseCases evidence;
    @Inject EvaluationUseCases evaluation;

    public record StartRequest(
            @NotNull(message = "La IPRESS es obligatoria") Long facilityId,
            @NotNull(message = "El año es obligatorio") Integer periodYear,
            @NotNull(message = "La categoría de la IPRESS es obligatoria") FacilityCategory facilityCategory) {
    }

    public record ScoreRequest(
            @NotNull(message = "El puntaje es obligatorio") @Min(value = 0, message = "Puntaje mínimo 0")
            @Max(value = 2, message = "Puntaje máximo 2") Integer score,
            @Size(max = 2000, message = "Justificación: máximo 2000 caracteres") String justification) {
    }

    // ── Life cycle ──

    @GET
    @PermissionsAllowed(Permisos.AUTOEVALUACION_VER)
    public Page<SelfAssessmentView> search(@RestQuery Long facilityId, @RestQuery Integer year,
                                           @RestQuery AssessmentStatus status,
                                           @RestQuery @DefaultValue("0") int page, @RestQuery @DefaultValue("20") int size) {
        return assessments.search(facilityId, year, status, PageRequest.of(page, size));
    }

    @POST
    @ResponseStatus(201)
    @PermissionsAllowed(Permisos.AUTOEVALUACION_GESTIONAR)
    public SelfAssessmentView start(@Valid StartRequest request) {
        return assessments.start(new StartSelfAssessment(request.facilityId(), request.periodYear(), request.facilityCategory()));
    }

    @GET
    @Path("/{id}")
    @PermissionsAllowed(Permisos.AUTOEVALUACION_VER)
    public SelfAssessmentView get(@PathParam("id") Long id) {
        return assessments.get(id);
    }

    /** The LEA tree of the assessment with evidence, reviews and scores: the main working screen. */
    @GET
    @Path("/{id}/checklist")
    @PermissionsAllowed(Permisos.AUTOEVALUACION_VER)
    public ChecklistView checklist(@PathParam("id") Long id) {
        return assessments.checklist(id);
    }

    @GET
    @Path("/{id}/result")
    @PermissionsAllowed(Permisos.AUTOEVALUACION_VER)
    public ResultView result(@PathParam("id") Long id) {
        return assessments.result(id);
    }

    /** Evaluator: closes the document upload (candado); the evaluatee can no longer change evidence. */
    @POST
    @Path("/{id}/evidence-lock")
    @PermissionsAllowed(Permisos.EVIDENCIAS_EVALUAR)
    public SelfAssessmentUseCases.LockResult lockEvidence(@PathParam("id") Long id) {
        return assessments.lockEvidence(id);
    }

    /** Evaluator: opens the document upload again. */
    @DELETE
    @Path("/{id}/evidence-lock")
    @PermissionsAllowed(Permisos.EVIDENCIAS_EVALUAR)
    public SelfAssessmentView unlockEvidence(@PathParam("id") Long id) {
        return assessments.unlockEvidence(id);
    }

    @POST
    @Path("/{id}/close")
    @PermissionsAllowed(Permisos.AUTOEVALUACION_GESTIONAR)
    public SelfAssessmentView close(@PathParam("id") Long id) {
        return assessments.close(id);
    }

    // ── Evidence ──

    /** Evidence of the assessment in a status (PENDING_REVIEW = the evaluator's queue). */
    @GET
    @Path("/{id}/evidence")
    @PermissionsAllowed(Permisos.AUTOEVALUACION_VER)
    public Page<EvidenceView> evidence(@PathParam("id") Long id,
                                       @RestQuery @DefaultValue("PENDING_REVIEW") SubmissionStatus status,
                                       @RestQuery @DefaultValue("0") int page, @RestQuery @DefaultValue("20") int size) {
        return evidence.listByStatus(id, status, PageRequest.of(page, size));
    }

    /** Evaluatee: evidence of an audit source, with one or more files ({@code files}) and/or a link. */
    @POST
    @Path("/{id}/evidence")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @ResponseStatus(201)
    @PermissionsAllowed(Permisos.EVIDENCIAS_REGISTRAR)
    public EvidenceView submitEvidence(@PathParam("id") Long id, @RestForm Long auditSourceId,
                                       @RestForm("files") List<FileUpload> files,
                                       @RestForm String externalUrl, @RestForm String notes) {
        if (auditSourceId == null) throw new DomainException("Indique la fuente auditable");
        return evidence.submit(new SubmitEvidence(id, auditSourceId, Uploads.toNewFiles(files), externalUrl, notes));
    }

    // ── Evaluator ──

    /** On-site verification of a condition (met or not, comment and photos in {@code files}). */
    @PUT
    @Path("/{id}/conditions/{conditionId}/review")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @PermissionsAllowed(Permisos.EVIDENCIAS_EVALUAR)
    public ConditionReviewView reviewCondition(@PathParam("id") Long id, @PathParam("conditionId") Long conditionId,
                                               @RestForm boolean met, @RestForm String comment,
                                               @RestForm("files") List<FileUpload> files) {
        return evaluation.reviewCondition(new ReviewCondition(id, conditionId, met, comment, Uploads.toNewFiles(files)));
    }

    @PUT
    @Path("/{id}/criteria/{criterionId}/score")
    @PermissionsAllowed(Permisos.EVIDENCIAS_EVALUAR)
    public CriterionScoreView score(@PathParam("id") Long id, @PathParam("criterionId") Long criterionId,
                                    @Valid ScoreRequest request) {
        return evaluation.scoreCriterion(new ScoreCriterion(id, criterionId, request.score(), request.justification()));
    }
}
