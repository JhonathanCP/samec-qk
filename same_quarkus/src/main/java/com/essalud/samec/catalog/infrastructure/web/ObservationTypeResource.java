package com.essalud.samec.catalog.infrastructure.web;

import java.util.List;

import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.RestQuery;

import com.essalud.samec.catalog.application.port.in.ObservationTypeUseCases;
import com.essalud.samec.catalog.domain.model.ObservationType;
import com.essalud.samec.catalog.infrastructure.web.CatalogRequests.ObservationTypeRequest;
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

/** Reasons an evaluator can choose when observing evidence. */
@Path("/catalog/observation-types")
public class ObservationTypeResource {

    @Inject
    ObservationTypeUseCases observationTypes;

    @GET
    public List<ObservationType> list(@RestQuery boolean activeOnly) {
        return activeOnly ? observationTypes.listActive() : observationTypes.list();
    }

    @GET
    @Path("/{id}")
    public ObservationType get(@PathParam("id") Long id) {
        return observationTypes.get(id);
    }

    @POST
    @ResponseStatus(201)
    @PermissionsAllowed(Permisos.CATALOGOS_CRUD)
    public ObservationType create(@Valid ObservationTypeRequest request) {
        return observationTypes.create(request.toModel());
    }

    @PUT
    @Path("/{id}")
    @PermissionsAllowed(Permisos.CATALOGOS_CRUD)
    public ObservationType update(@PathParam("id") Long id, @Valid ObservationTypeRequest request) {
        return observationTypes.update(id, request.toModel());
    }

    @DELETE
    @Path("/{id}")
    @ResponseStatus(204)
    @PermissionsAllowed(Permisos.CATALOGOS_CRUD)
    public void delete(@PathParam("id") Long id) {
        observationTypes.delete(id);
    }
}
