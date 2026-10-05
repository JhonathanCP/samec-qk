package com.essalud.samec.security.ratelimit;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

import com.essalud.samec.security.IpCliente;
import com.essalud.samec.shared.config.SamecConfig;

import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Límite de solicitudes por usuario o por IP. Corre después de la autenticación (para conocer al
 * usuario) y complementa al límite de Nginx: si alguien llama directo al puerto del core sin pasar
 * por el proxy, igual tiene tope. Los valores son amplios a propósito (varios usuarios de un
 * hospital salen por la misma IP); la protección fina contra adivinar contraseñas u OTP es por
 * cuenta (ver {@link IntentosFallidos}).
 */
public class RateLimitFilter {

    private static final Logger LOG = Logger.getLogger(RateLimitFilter.class);

    enum Clave { IP, USUARIO }

    record Regla(String metodo, Pattern ruta, RateLimiter.Limite limite, Clave clave, String mensaje) {
        boolean aplica(String metodoReq, String rutaReq) {
            return (metodo == null || metodo.equalsIgnoreCase(metodoReq)) && ruta.matcher(rutaReq).matches();
        }
    }

    private static final String MSJ_GENERAL = "Demasiadas solicitudes seguidas. Espere %d segundos e intente nuevamente.";

    /** Reglas específicas, en orden; la primera que coincide es la que se aplica. */
    static final List<Regla> REGLAS = List.of(
            new Regla("POST", Pattern.compile("/auth/login"),
                    new RateLimiter.Limite("login", 30, Duration.ofMinutes(1)), Clave.IP,
                    "Demasiados intentos de inicio de sesión desde su red. Espere %d segundos e intente nuevamente."),
            new Regla("POST", Pattern.compile("/auth/olvide-password"),
                    new RateLimiter.Limite("olvide", 10, Duration.ofMinutes(15)), Clave.IP,
                    "Demasiadas solicitudes de recuperación de contraseña. Espere %d segundos e intente nuevamente."),
            new Regla("POST", Pattern.compile("/auth/restablecer-password"),
                    new RateLimiter.Limite("restablecer", 20, Duration.ofMinutes(15)), Clave.IP,
                    "Demasiados intentos de restablecer la contraseña. Espere %d segundos e intente nuevamente."),
            new Regla("POST", Pattern.compile("/usuarios/autorregistro"),
                    new RateLimiter.Limite("registro", 10, Duration.ofHours(1)), Clave.IP,
                    "Demasiadas solicitudes de registro desde su red. Espere %d segundos e intente nuevamente."),
            new Regla("POST", Pattern.compile("/auth/refresh"),
                    new RateLimiter.Limite("refresh", 120, Duration.ofMinutes(1)), Clave.IP, MSJ_GENERAL));

    static final RateLimiter.Limite GENERAL_USUARIO = new RateLimiter.Limite("general-usuario", 600, Duration.ofMinutes(1));
    static final RateLimiter.Limite GENERAL_IP = new RateLimiter.Limite("general-ip", 300, Duration.ofMinutes(1));
    /** Para no inundar el log durante un ataque: a lo sumo un aviso por clave y minuto. */
    private static final RateLimiter.Limite LOG_LIMITE = new RateLimiter.Limite("log", 1, Duration.ofMinutes(1));

    private final RateLimiter limiter;
    private final IpCliente ipCliente;
    private final boolean habilitado;

    @Inject
    CurrentIdentityAssociation identidad;

    @Inject
    public RateLimitFilter(RateLimiter limiter, IpCliente ipCliente, SamecConfig config) {
        this.limiter = limiter;
        this.ipCliente = ipCliente;
        this.habilitado = config.ratelimit().enabled();
    }

    @ServerRequestFilter
    public Response limitar(ContainerRequestContext ctx, HttpServerRequest request) {
        if (!habilitado || "OPTIONS".equalsIgnoreCase(ctx.getMethod())) return null;
        String ruta = ctx.getUriInfo().getPath();
        if (ruta.equals("/health") || ruta.startsWith("/q/")) return null;

        String usuario = usuarioAutenticado();
        String ip = ipCliente.de(request);

        Regla regla = REGLAS.stream().filter(r -> r.aplica(ctx.getMethod(), ruta)).findFirst().orElse(null);
        RateLimiter.Limite limite;
        String clave;
        String mensaje;
        if (regla != null) {
            limite = regla.limite();
            clave = regla.clave() == Clave.USUARIO && usuario != null ? "u:" + usuario : "ip:" + ip;
            mensaje = regla.mensaje();
        } else {
            limite = usuario != null ? GENERAL_USUARIO : GENERAL_IP;
            clave = usuario != null ? "u:" + usuario : "ip:" + ip;
            mensaje = MSJ_GENERAL;
        }

        RateLimiter.Resultado r = limiter.consumir(clave, limite);
        if (r.permitido()) return null;
        if (limiter.consumir(limite.nombre() + "|" + clave, LOG_LIMITE).permitido()) {
            LOG.warnf("Límite '%s' excedido por %s en %s %s (ip %s)", limite.nombre(), clave, ctx.getMethod(), ruta, ip);
        }
        return demasiadasSolicitudes(String.format(mensaje, r.reintentarEnSegundos()), r.reintentarEnSegundos());
    }

    public static Response demasiadasSolicitudes(String mensaje, long reintentarEnSegundos) {
        return Response.status(429)
                .header("Retry-After", String.valueOf(reintentarEnSegundos))
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Map.of("message", mensaje, "retryAfterSeconds", reintentarEnSegundos))
                .build();
    }

    private String usuarioAutenticado() {
        SecurityIdentity id = identidad.getIdentity();
        return id == null || id.isAnonymous() ? null : id.getPrincipal().getName();
    }
}
