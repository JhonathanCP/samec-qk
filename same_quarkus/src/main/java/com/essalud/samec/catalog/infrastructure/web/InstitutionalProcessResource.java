package com.essalud.samec.catalog.infrastructure.web;

import java.util.List;

import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.RestQuery;

import com.essalud.samec.catalog.application.port.in.CatalogTreeUseCase;
import com.essalud.samec.catalog.application.port.in.CatalogTreeUseCase.Usage;
import com.essalud.samec.catalog.application.port.in.InstitutionalProcessUseCases;
import com.essalud.samec.catalog.domain.model.InstitutionalProcess;
import com.essalud.samec.catalog.infrastructure.web.CatalogRequests.InstitutionalProcessRequest;
import com.essalud.samec.security.Permisos;

import io.quarkus.security.PermissionsAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

@Path("/catalog/institutional-processes")
public class InstitutionalProcessResource {

    @Inject
    InstitutionalProcessUseCases processes;

    @Inject
    CatalogTreeUseCase tree;

    /** All processes; with {@code parentId} only its sub-processes, with {@code roots=true} only top-level ones. */
    @GET
    public List<InstitutionalProcess> list(@RestQuery Long parentId, @RestQuery boolean roots) {
        if (parentId != null || roots) return processes.listChildren(parentId);
        return processes.list();
    }

    @GET
    @Path("/{id}")
    public InstitutionalProcess get(@PathParam("id") Long id) {
        return processes.get(id);
    }

    /** Conditions (and their criteria) in this process or sub-process. */
    @GET
    @Path("/{id}/usage")
    public List<Usage> usage(@PathParam("id") Long id) {
        processes.get(id);
        return tree.usagesOfInstitutionalProcess(id);
    }

    @POST
    @ResponseStatus(201)
    @PermissionsAllowed(Permisos.CATALOGOS_CRUD)
    public InstitutionalProcess create(@Valid InstitutionalProcessRequest request) {
        return processes.create(request.toModel());
    }

    @PUT
    @Path("/{id}")
    @PermissionsAllowed(Permisos.CATALOGOS_CRUD)
    public InstitutionalProcess update(@PathParam("id") Long id, @Valid InstitutionalProcessRequest request) {
        return processes.update(id, request.toModel());
    }

    @DELETE
    @Path("/{id}")
    @ResponseStatus(204)
    @PermissionsAllowed(Permisos.CATALOGOS_CRUD)
    public void delete(@PathParam("id") Long id) {
        processes.delete(id);
    }
}
