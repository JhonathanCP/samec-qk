package com.essalud.samec.assessment.infrastructure.web;

import com.essalud.samec.assessment.application.port.in.AssessmentViews.ConditionReviewView;
import com.essalud.samec.assessment.application.port.in.EvaluationUseCases;
import com.essalud.samec.security.Permisos;

import io.quarkus.security.PermissionsAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

/** Photos of an on-site verification (they are added with PUT .../conditions/{id}/review). */
@Path("/condition-reviews")
public class ConditionReviewResource {

    @Inject
    EvaluationUseCases evaluation;

    @DELETE
    @Path("/{id}/files/{fileId}")
    @PermissionsAllowed(Permisos.EVIDENCIAS_EVALUAR)
    public ConditionReviewView removeFile(@PathParam("id") Long id, @PathParam("fileId") Long fileId) {
        return evaluation.removeConditionReviewFile(id, fileId);
    }
}
