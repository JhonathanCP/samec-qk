package com.essalud.samec.organizacion;

import java.util.List;

import org.jboss.resteasy.reactive.ResponseStatus;

import com.essalud.samec.organizacion.OrganizacionService.IpressDto;
import com.essalud.samec.organizacion.OrganizacionService.IpressRequest;
import com.essalud.samec.organizacion.OrganizacionService.MacroRegionDto;
import com.essalud.samec.organizacion.OrganizacionService.RedDto;
import com.essalud.samec.organizacion.OrganizacionService.RedRequest;
import com.essalud.samec.security.Permisos;
import com.essalud.samec.usuarios.dto.UsuarioDtos.EstadoRequest;

import io.quarkus.security.PermissionsAllowed;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

/**
 * Catálogos de MacroRegiones, Redes e IPRESS. La lectura de Redes e IPRESS es pública porque la
 * necesita el formulario de autorregistro; la escritura exige catalogos.crud.
 */
@Path("/")
public class OrganizacionResource {

    @Inject
    OrganizacionService servicio;

    @GET
    @Path("macroregiones")
    public List<MacroRegionDto> macroRegiones() {
        return servicio.macroRegiones();
    }

    // ── Redes ──

    @GET
    @Path("redes")
    @PermitAll
    public List<RedDto> redes(@QueryParam("soloActivas") @DefaultValue("true") boolean soloActivas) {
        return servicio.redes(soloActivas);
    }

    @GET
    @Path("redes/{id}")
    @PermitAll
    public RedDto red(@PathParam("id") Long id) {
        return servicio.red(id);
    }

    @POST
    @Path("redes")
    @ResponseStatus(201)
    @PermissionsAllowed(Permisos.CATALOGOS_CRUD)
    public RedDto crearRed(@Valid RedRequest req) {
        return servicio.guardarRed(null, req);
    }

    @PUT
    @Path("redes/{id}")
    @PermissionsAllowed(Permisos.CATALOGOS_CRUD)
    public RedDto actualizarRed(@PathParam("id") Long id, @Valid RedRequest req) {
        return servicio.guardarRed(id, req);
    }

    @PATCH
    @Path("redes/{id}/estado")
    @PermissionsAllowed(Permisos.CATALOGOS_CRUD)
    public RedDto estadoRed(@PathParam("id") Long id, @Valid EstadoRequest req) {
        return servicio.cambiarEstadoRed(id, req.activo());
    }

    // ── IPRESS ──

    @GET
    @Path("ipress")
    @PermitAll
    public List<IpressDto> ipress(@QueryParam("idRed") Long idRed,
                                  @QueryParam("soloActivas") @DefaultValue("true") boolean soloActivas) {
        return servicio.ipress(idRed, soloActivas);
    }

    @GET
    @Path("ipress/{id}")
    @PermitAll
    public IpressDto ipress(@PathParam("id") Long id) {
        return servicio.ipress(id);
    }

    @POST
    @Path("ipress")
    @ResponseStatus(201)
    @PermissionsAllowed(Permisos.CATALOGOS_CRUD)
    public IpressDto crearIpress(@Valid IpressRequest req) {
        return servicio.guardarIpress(null, req);
    }

    @PUT
    @Path("ipress/{id}")
    @PermissionsAllowed(Permisos.CATALOGOS_CRUD)
    public IpressDto actualizarIpress(@PathParam("id") Long id, @Valid IpressRequest req) {
        return servicio.guardarIpress(id, req);
    }

    @PATCH
    @Path("ipress/{id}/estado")
    @PermissionsAllowed(Permisos.CATALOGOS_CRUD)
    public IpressDto estadoIpress(@PathParam("id") Long id, @Valid EstadoRequest req) {
        return servicio.cambiarEstadoIpress(id, req.activo());
    }
}
