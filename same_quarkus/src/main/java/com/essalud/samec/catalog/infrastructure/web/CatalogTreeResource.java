package com.essalud.samec.catalog.infrastructure.web;

import java.util.List;

import com.essalud.samec.catalog.application.port.in.CatalogTreeUseCase;
import com.essalud.samec.catalog.application.port.in.CatalogTreeUseCase.MacroProcessNode;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/** The whole catalog as a light tree (macro process → standard → criterion summary). Needs a session. */
@Path("/catalog/tree")
public class CatalogTreeResource {

    @Inject
    CatalogTreeUseCase tree;

    @GET
    public List<MacroProcessNode> tree() {
        return tree.tree();
    }
}
