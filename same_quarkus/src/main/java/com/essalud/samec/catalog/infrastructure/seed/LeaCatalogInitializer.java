package com.essalud.samec.catalog.infrastructure.seed;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

import org.jboss.logging.Logger;

import com.essalud.samec.catalog.application.port.in.LeaCatalogSeedUseCase;
import com.essalud.samec.catalog.application.port.in.LeaCatalogSeedUseCase.LeaCatalog;
import com.essalud.samec.catalog.application.port.in.LeaCatalogSeedUseCase.ObservationTypeSeed;
import com.essalud.samec.shared.config.SamecConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Loads the LEA catalog at startup when it is empty (first deploy, new database), from
 * {@value #RESOURCE}: the manual "00. Manual completo LEA ESSALUD (Y-O).xlsx" converted with
 * scripts/lea_a_json.py. Once loaded, the catalog is maintained through its API. Off with
 * samec.seed.lea=false (tests).
 */
@ApplicationScoped
public class LeaCatalogInitializer {

    private static final Logger LOG = Logger.getLogger(LeaCatalogInitializer.class);

    static final String RESOURCE = "seed/lea.json";

    /** Observation reasons of the prototype; more can be added through the catalog API. */
    static final List<ObservationTypeSeed> OBSERVATION_TYPES = List.of(
            new ObservationTypeSeed("DOCUMENT_MISMATCH", "El documento no corresponde"),
            new ObservationTypeSeed("WRONG_YEAR", "El documento no corresponde al año"),
            new ObservationTypeSeed("OTHER", "Otro (detallar en el comentario)"));

    @Inject
    LeaCatalogSeedUseCase seeder;

    @Inject
    ObjectMapper mapper;

    @Inject
    SamecConfig config;

    void onStart(@Observes StartupEvent event) {
        if (!config.seed().lea()) return;
        try {
            seeder.loadIfEmpty(read()).ifPresent(r -> LOG.infof(
                    "Catálogo LEA cargado: %d macroprocesos, %d estándares, %d criterios, %d condiciones, "
                            + "%d documentos normativos, %d procesos institucionales",
                    r.macroProcesses(), r.standards(), r.criteria(), r.conditions(), r.normativeDocuments(),
                    r.institutionalProcesses()));
            int types = seeder.loadObservationTypesIfEmpty(OBSERVATION_TYPES);
            if (types > 0) LOG.infof("Tipos de observación creados: %d", types);
        } catch (RuntimeException e) {
            // The application still starts (users, catalogs); the error says what to fix in the LEA.
            LOG.errorf(e, "No se pudo cargar el catálogo LEA desde %s; no se guardó nada", RESOURCE);
        }
    }

    /** The LEA as converted from the Excel manual. */
    public LeaCatalog read() {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("No se encontró " + RESOURCE);
            return mapper.readValue(in, LeaCatalog.class);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + RESOURCE, e);
        }
    }
}
