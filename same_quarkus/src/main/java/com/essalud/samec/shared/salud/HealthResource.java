package com.essalud.samec.shared.salud;

import java.util.Map;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/**
 * Chequeo simple para Nginx y monitoreo externo. El detalle (BD, Kafka) está en /q/health, que
 * Nginx no publica hacia afuera.
 */
@Path("/health")
@PermitAll
public class HealthResource {

    @GET
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
