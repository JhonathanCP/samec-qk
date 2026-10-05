package com.essalud.samec.catalog.infrastructure.web;

import java.util.List;

import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.RestQuery;

import com.essalud.samec.catalog.application.port.in.CriterionUseCases;
import com.essalud.samec.catalog.domain.model.Criterion;
import com.essalud.samec.catalog.domain.model.FacilityCategory;
import com.essalud.samec.catalog.infrastructure.web.CatalogRequests.CriterionRequest;
import com.essalud.samec.security.Permisos;
import com.essalud.samec.shared.domain.Page;
import com.essalud.samec.shared.domain.PageRequest;

import io.quarkus.security.PermissionsAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

/**
 * Criteria of the LEA with their conditions, audit sources and normative references. On update
 * the whole criterion is sent: conditions and sources with their id are kept, those without id are
 * created and those not sent are removed.
 */
@Path("/catalog/criteria")
public class CriterionResource {

    @Inject
    CriterionUseCases criteria;

    /** Paged; or every criterion of a standard, or every criterion that applies to a facility category. */
    @GET
    public Page<Criterion> list(@RestQuery Long standardId, @RestQuery FacilityCategory category,
                                @RestQuery @DefaultValue("0") int page, @RestQuery @DefaultValue("50") int size) {
        if (standardId != null) return whole(criteria.listByStandard(standardId));
        if (category != null) return whole(criteria.listApplicableTo(category));
        return criteria.list(PageRequest.of(page, size));
    }

    @GET
    @Path("/{id}")
    public Criterion get(@PathParam("id") Long id) {
        return criteria.get(id);
    }

    @POST
    @ResponseStatus(201)
    @PermissionsAllowed(Permisos.CATALOGOS_CRUD)
    public Criterion create(@Valid CriterionRequest request) {
        return criteria.create(request.toModel());
    }

    @PUT
    @Path("/{id}")
    @PermissionsAllowed(Permisos.CATALOGOS_CRUD)
    public Criterion update(@PathParam("id") Long id, @Valid CriterionRequest request) {
        return criteria.update(id, request.toModel());
    }

    @DELETE
    @Path("/{id}")
    @ResponseStatus(204)
    @PermissionsAllowed(Permisos.CATALOGOS_CRUD)
    public void delete(@PathParam("id") Long id) {
        criteria.delete(id);
    }

    private static Page<Criterion> whole(List<Criterion> list) {
        return new Page<>(list, list.size(), 1, 0, Math.max(1, list.size()));
    }
}
