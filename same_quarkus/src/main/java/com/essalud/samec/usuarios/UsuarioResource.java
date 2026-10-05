package com.essalud.samec.usuarios;

import java.util.List;

import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.RestCookie;

import com.essalud.samec.auth.AuthResource;
import com.essalud.samec.security.Permisos;
import com.essalud.samec.shared.bitacora.BitacoraService;
import com.essalud.samec.usuarios.dto.UsuarioDtos.AutorregistroRequest;
import com.essalud.samec.usuarios.dto.UsuarioDtos.CambiarPasswordRequest;
import com.essalud.samec.usuarios.dto.UsuarioDtos.EstadoRequest;
import com.essalud.samec.usuarios.dto.UsuarioDtos.MensajeResponse;
import com.essalud.samec.usuarios.dto.UsuarioDtos.ResolverSolicitudRequest;
import com.essalud.samec.usuarios.dto.UsuarioDtos.RolDto;
import com.essalud.samec.usuarios.dto.UsuarioDtos.UsuarioDto;
import com.essalud.samec.usuarios.dto.UsuarioDtos.UsuarioRequest;

import io.quarkus.security.PermissionsAllowed;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

/**
 * Usuarios. El permiso abre la puerta; el alcance (quién puede tocar a quién) lo valida
 * UsuarioService con los datos.
 */
@Path("/usuarios")
public class UsuarioResource {

    @Inject
    UsuarioService servicio;

    @Inject
    BitacoraService bitacora;

    /** Listado según el alcance de quien consulta. */
    @GET
    public List<UsuarioDto> listar() {
        return servicio.listarVisibles();
    }

    @GET
    @Path("/{id}")
    public UsuarioDto obtener(@PathParam("id") Long id) {
        return servicio.obtener(id);
    }

    @GET
    @Path("/{id}/bitacora")
    public List<BitacoraService.Entrada> historial(@PathParam("id") Long id) {
        servicio.obtener(id); // mismo acceso que ver el usuario
        return bitacora.listar(UsuarioService.BITACORA, id);
    }

    @GET
    @Path("/roles-asignables")
    @PermissionsAllowed({ Permisos.USUARIOS_CRUD, Permisos.USUARIOS_CRUD_RED, Permisos.USUARIOS_CRUD_IPRESS })
    public List<RolDto> rolesAsignables() {
        return servicio.rolesAsignables();
    }

    @POST
    @ResponseStatus(201)
    @PermissionsAllowed({ Permisos.USUARIOS_CRUD, Permisos.USUARIOS_CRUD_RED, Permisos.USUARIOS_CRUD_IPRESS })
    public UsuarioDto crear(@Valid UsuarioRequest req) {
        return servicio.crear(req);
    }

    @PUT
    @Path("/{id}")
    @PermissionsAllowed({ Permisos.USUARIOS_CRUD, Permisos.USUARIOS_CRUD_RED, Permisos.USUARIOS_CRUD_IPRESS })
    public UsuarioDto actualizar(@PathParam("id") Long id, @Valid UsuarioRequest req) {
        return servicio.actualizar(id, req);
    }

    @PATCH
    @Path("/{id}/estado")
    @PermissionsAllowed({ Permisos.USUARIOS_CRUD, Permisos.USUARIOS_CRUD_RED, Permisos.USUARIOS_CRUD_IPRESS })
    public UsuarioDto cambiarEstado(@PathParam("id") Long id, @Valid EstadoRequest req) {
        return servicio.cambiarEstado(id, req.activo());
    }

    @POST
    @Path("/{id}/restablecer-password")
    @PermissionsAllowed({ Permisos.USUARIOS_CRUD, Permisos.USUARIOS_CRUD_RED, Permisos.USUARIOS_CRUD_IPRESS })
    public MensajeResponse restablecerPassword(@PathParam("id") Long id) {
        servicio.restablecerPassword(id);
        return new MensajeResponse("Se envió al usuario un enlace para establecer una nueva contraseña.");
    }

    /** Cambio de la propia contraseña (cualquier usuario con sesión). */
    @POST
    @Path("/me/password")
    public MensajeResponse cambiarMiPassword(@Valid CambiarPasswordRequest req,
                                             @RestCookie(AuthResource.COOKIE_REFRESH) String refreshToken) {
        servicio.cambiarMiPassword(req.passwordActual(), req.passwordNueva(), refreshToken);
        return new MensajeResponse("Contraseña actualizada correctamente.");
    }

    // ── Autorregistro ──

    @POST
    @Path("/autorregistro")
    @PermitAll
    public MensajeResponse autorregistro(@Valid AutorregistroRequest req) {
        servicio.autorregistrar(req);
        return new MensajeResponse("Su solicitud fue registrada y quedará pendiente hasta que el Gestor de "
                + "Calidad de Sede Central la revise. Le avisaremos por correo.");
    }

    @GET
    @Path("/solicitudes")
    @PermissionsAllowed(Permisos.USUARIOS_SOLICITUDES)
    public List<UsuarioDto> solicitudes(@QueryParam("estado") EstadoRegistro estado,
                                        @QueryParam("idRed") Long idRed,
                                        @QueryParam("idIpress") Long idIpress) {
        return servicio.solicitudes(estado, idRed, idIpress);
    }

    @PATCH
    @Path("/{id}/registro-estado")
    @PermissionsAllowed(Permisos.USUARIOS_SOLICITUDES)
    public UsuarioDto resolverSolicitud(@PathParam("id") Long id, @Valid ResolverSolicitudRequest req) {
        return servicio.resolverSolicitud(id, req.estado(), req.comentario());
    }
}
