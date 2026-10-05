package com.essalud.samec.auth;

import org.jboss.resteasy.reactive.RestCookie;

import com.essalud.samec.auth.AuthService.LoginRechazado;
import com.essalud.samec.auth.AuthService.SesionIniciada;
import com.essalud.samec.security.IpCliente;
import com.essalud.samec.security.UsuarioActual;
import com.essalud.samec.security.ratelimit.RateLimitFilter;
import com.essalud.samec.shared.config.SamecConfig;
import com.essalud.samec.shared.error.ErrorResponse;
import com.essalud.samec.usuarios.dto.UsuarioDtos.MensajeResponse;
import com.essalud.samec.usuarios.dto.UsuarioDtos.UsuarioSesionDto;

import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

/**
 * Sesión: login, refresh, logout, usuario actual y recuperación de contraseña.
 *
 * El refresh token viaja solo en una cookie httpOnly (no legible por JS): un XSS no puede robarlo.
 * El access token va en el cuerpo y el frontend lo envía en Authorization: Bearer.
 */
@Path("/auth")
public class AuthResource {

    public static final String COOKIE_REFRESH = "samecRefresh";

    @Inject AuthService auth;
    @Inject UsuarioActual actual;
    @Inject SamecConfig config;
    @Inject IpCliente ipCliente;

    public record LoginRequest(@NotBlank(message = "Debe indicar su usuario.") String username,
                               @NotBlank(message = "Debe indicar su contraseña.") String password,
                               String recaptchaToken) {
    }

    public record LoginResponse(String accessToken, String tokenType, long expiresIn, UsuarioSesionDto user) {
    }

    public record OlvidePasswordRequest(@NotBlank(message = "Debe indicar su usuario o correo.") String username,
                                        String recaptchaToken) {
    }

    public record RestablecerPasswordRequest(@NotBlank(message = "Debe indicar su usuario o correo.") String username,
                                             @NotBlank(message = "Debe indicar el código recibido.") String otp,
                                             @NotBlank(message = "Debe indicar la nueva contraseña.") String password) {
    }

    @POST
    @Path("/login")
    @PermitAll
    public Response login(@Valid LoginRequest req, @Context HttpServerRequest request) {
        try {
            SesionIniciada s = auth.login(req.username(), req.password(), req.recaptchaToken(),
                    ipCliente.de(request), request.getHeader(HttpHeaders.USER_AGENT));
            return conSesion(s);
        } catch (LoginRechazado e) {
            return rechazo(e, "/auth/login");
        }
    }

    @POST
    @Path("/refresh")
    @PermitAll
    public Response refresh(@RestCookie(COOKIE_REFRESH) String refreshToken, @Context HttpServerRequest request) {
        try {
            return conSesion(auth.renovar(refreshToken, ipCliente.de(request),
                    request.getHeader(HttpHeaders.USER_AGENT)));
        } catch (LoginRechazado e) {
            return Response.fromResponse(rechazo(e, "/auth/refresh")).cookie(cookie("", 0)).build();
        }
    }

    @POST
    @Path("/logout")
    @PermitAll
    public Response logout(@RestCookie(COOKIE_REFRESH) String refreshToken) {
        auth.cerrarSesion(refreshToken);
        return Response.ok(new MensajeResponse("Sesión cerrada.")).cookie(cookie("", 0)).build();
    }

    /** Usuario de la sesión, con roles y permisos vigentes. */
    @GET
    @Path("/me")
    public UsuarioSesionDto me() {
        return auth.sesion(actual.id());
    }

    @POST
    @Path("/olvide-password")
    @PermitAll
    public MensajeResponse olvidePassword(@Valid OlvidePasswordRequest req) {
        auth.olvidePassword(req.username(), req.recaptchaToken());
        return new MensajeResponse("Si el usuario existe, se envió un código a su correo institucional.");
    }

    @POST
    @Path("/restablecer-password")
    @PermitAll
    public MensajeResponse restablecerPassword(@Valid RestablecerPasswordRequest req) {
        auth.restablecerPassword(req.username(), req.otp(), req.password());
        return new MensajeResponse("Contraseña actualizada correctamente.");
    }

    private Response conSesion(SesionIniciada s) {
        return Response.ok(new LoginResponse(s.accessToken(), "Bearer", s.expiresIn(), s.usuario()))
                .cookie(cookie(s.refresh().token(), (int) s.refresh().vigencia().toSeconds()))
                .build();
    }

    private static Response rechazo(LoginRechazado e, String path) {
        if (e.status == 429) {
            return Response.fromResponse(RateLimitFilter.demasiadasSolicitudes(e.getMessage(), e.reintentarEnSegundos))
                    .build();
        }
        return Response.status(e.status).entity(ErrorResponse.de(e.getMessage(), path)).build();
    }

    private NewCookie cookie(String valor, int maxAgeSegundos) {
        return new NewCookie.Builder(COOKIE_REFRESH)
                .value(valor)
                .path("/")
                .httpOnly(true)
                .secure(config.cookie().secure())
                .sameSite(NewCookie.SameSite.LAX)
                .maxAge(maxAgeSegundos)
                .build();
    }
}
