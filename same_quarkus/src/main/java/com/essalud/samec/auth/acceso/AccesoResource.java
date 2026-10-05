package com.essalud.samec.auth.acceso;

import com.essalud.samec.security.Permisos;
import com.essalud.samec.shared.paginacion.Pagina;

import io.quarkus.security.PermissionsAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

/** Historial de inicios de sesión (solo Administrador). */
@Path("/accesos")
public class AccesoResource {

    @Inject
    AccesoService accesos;

    @GET
    @PermissionsAllowed(Permisos.ACCESOS_VER)
    public Pagina<AccesoService.AccesoDto> listar(@QueryParam("username") String username,
                                                  @QueryParam("exito") Boolean exito,
                                                  @QueryParam("page") @DefaultValue("0") int page,
                                                  @QueryParam("size") @DefaultValue("50") int size) {
        return accesos.listar(username, exito, page, size);
    }
}
