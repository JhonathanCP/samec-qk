package com.essalud.samec.catalog.infrastructure.web;

import java.util.List;

import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.RestQuery;

import com.essalud.samec.catalog.application.port.in.StandardUseCases;
import com.essalud.samec.catalog.domain.model.Standard;
import com.essalud.samec.catalog.infrastructure.web.CatalogRequests.StandardRequest;
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

@Path("/catalog/standards")
public class StandardResource {

    @Inject
    StandardUseCases standards;

    /** All standards, or those of one macro process. */
    @GET
    public List<Standard> list(@RestQuery Long macroProcessId) {
        return macroProcessId == null ? standards.list() : standards.listByMacroProcess(macroProcessId);
    }

    @GET
    @Path("/{id}")
    public Standard get(@PathParam("id") Long id) {
        return standards.get(id);
    }

    @POST
    @ResponseStatus(201)
    @PermissionsAllowed(Permisos.CATALOGOS_CRUD)
    public Standard create(@Valid StandardRequest request) {
        return standards.create(request.toModel());
    }

    @PUT
    @Path("/{id}")
    @PermissionsAllowed(Permisos.CATALOGOS_CRUD)
    public Standard update(@PathParam("id") Long id, @Valid StandardRequest request) {
        return standards.update(id, request.toModel());
    }

    @DELETE
    @Path("/{id}")
    @ResponseStatus(204)
    @PermissionsAllowed(Permisos.CATALOGOS_CRUD)
    public void delete(@PathParam("id") Long id) {
        standards.delete(id);
    }
}
