package com.essalud.samec.catalog.infrastructure.web;

import java.util.List;

import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.RestQuery;

import com.essalud.samec.catalog.application.port.in.CatalogTreeUseCase;
import com.essalud.samec.catalog.application.port.in.CatalogTreeUseCase.Usage;
import com.essalud.samec.catalog.application.port.in.NormativeDocumentUseCases;
import com.essalud.samec.catalog.domain.model.NormativeDocument;
import com.essalud.samec.catalog.infrastructure.web.CatalogRequests.NormativeDocumentRequest;
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

@Path("/catalog/normative-documents")
public class NormativeDocumentResource {

    @Inject
    NormativeDocumentUseCases documents;

    @Inject
    CatalogTreeUseCase tree;

    @GET
    public Page<NormativeDocument> list(@RestQuery @DefaultValue("0") int page, @RestQuery @DefaultValue("50") int size) {
        return documents.list(PageRequest.of(page, size));
    }

    @GET
    @Path("/{id}")
    public NormativeDocument get(@PathParam("id") Long id) {
        return documents.get(id);
    }

    /** Conditions (and their criteria) that cite this document. */
    @GET
    @Path("/{id}/usage")
    public List<Usage> usage(@PathParam("id") Long id) {
        documents.get(id);
        return tree.usagesOfNormativeDocument(id);
    }

    @POST
    @ResponseStatus(201)
    @PermissionsAllowed(Permisos.CATALOGOS_CRUD)
    public NormativeDocument create(@Valid NormativeDocumentRequest request) {
        return documents.create(request.toModel());
    }

    @PUT
    @Path("/{id}")
    @PermissionsAllowed(Permisos.CATALOGOS_CRUD)
    public NormativeDocument update(@PathParam("id") Long id, @Valid NormativeDocumentRequest request) {
        return documents.update(id, request.toModel());
    }

    @DELETE
    @Path("/{id}")
    @ResponseStatus(204)
    @PermissionsAllowed(Permisos.CATALOGOS_CRUD)
    public void delete(@PathParam("id") Long id) {
        documents.delete(id);
    }
}
