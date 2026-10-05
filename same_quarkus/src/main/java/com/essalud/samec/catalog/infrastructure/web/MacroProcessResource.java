package com.essalud.samec.catalog.infrastructure.web;

import java.util.List;

import org.jboss.resteasy.reactive.ResponseStatus;

import com.essalud.samec.catalog.application.port.in.MacroProcessUseCases;
import com.essalud.samec.catalog.domain.model.MacroProcess;
import com.essalud.samec.catalog.infrastructure.web.CatalogRequests.MacroProcessRequest;
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

/** Macro processes of the LEA. Reading needs a session; changes need the catalog permission. */
@Path("/catalog/macro-processes")
public class MacroProcessResource {

    @Inject
    MacroProcessUseCases macroProcesses;

    @GET
    public List<MacroProcess> list() {
        return macroProcesses.list();
    }

    @GET
    @Path("/{id}")
    public MacroProcess get(@PathParam("id") Long id) {
        return macroProcesses.get(id);
    }

    @POST
    @ResponseStatus(201)
    @PermissionsAllowed(Permisos.CATALOGOS_CRUD)
    public MacroProcess create(@Valid MacroProcessRequest request) {
        return macroProcesses.create(request.toModel());
    }

    @PUT
    @Path("/{id}")
    @PermissionsAllowed(Permisos.CATALOGOS_CRUD)
    public MacroProcess update(@PathParam("id") Long id, @Valid MacroProcessRequest request) {
        return macroProcesses.update(id, request.toModel());
    }

    @DELETE
    @Path("/{id}")
    @ResponseStatus(204)
    @PermissionsAllowed(Permisos.CATALOGOS_CRUD)
    public void delete(@PathParam("id") Long id) {
        macroProcesses.delete(id);
    }
}
