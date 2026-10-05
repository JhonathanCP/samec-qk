package com.essalud.samec.auth;

import java.time.LocalDateTime;

import com.essalud.samec.auth.acceso.AccesoRegistro.MotivoAcceso;
import com.essalud.samec.auth.acceso.AccesoService;
import com.essalud.samec.auth.password.PasswordResetService;
import com.essalud.samec.security.PasswordHasher;
import com.essalud.samec.security.RecaptchaService;
import com.essalud.samec.security.ratelimit.IntentosFallidos;
import com.essalud.samec.security.ratelimit.RateLimiter;
import com.essalud.samec.security.token.TokenService;
import com.essalud.samec.security.token.TokenService.RefreshEmitido;
import com.essalud.samec.shared.error.ReglaNegocioException;
import com.essalud.samec.usuarios.EstadoRegistro;
import com.essalud.samec.usuarios.PoliticaPassword;
import com.essalud.samec.usuarios.Usuario;
import com.essalud.samec.usuarios.UsuarioRepository;
import com.essalud.samec.usuarios.dto.UsuarioDtos.UsuarioSesionDto;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Inicio y renovación de sesión, y recuperación de contraseña. Las reglas vienen de Auditorías:
 * <ul>
 *   <li>usuario inexistente, contraseña incorrecta y cuenta bloqueada responden igual (no se revela
 *   qué cuentas existen);</li>
 *   <li>tras 5 contraseñas incorrectas la cuenta se bloquea 15 min, aunque los intentos vengan de
 *   muchas IP;</li>
 *   <li>los motivos "pendiente", "rechazada" y "desactivada" solo se dicen a quien sabe la contraseña.</li>
 * </ul>
 */
@ApplicationScoped
public class AuthService {

    static final String MSJ_CREDENCIALES = "Usuario o contraseña incorrectos.";
    static final String MSJ_PENDIENTE =
            "Su solicitud de registro aún está pendiente de aprobación por el Gestor de Calidad de Sede Central.";
    static final String MSJ_RECHAZADO = "Su solicitud de registro fue rechazada. Contacte al administrador del sistema.";
    static final String MSJ_DESACTIVADA = "Su cuenta está desactivada. Contacte al administrador del sistema.";
    static final String MSJ_RECAPTCHA = "No se pudo verificar reCAPTCHA. Intente nuevamente.";

    /** Correos de recuperación por cuenta: evita usar "Olvidé mi contraseña" para saturar un buzón. */
    private static final RateLimiter.Limite CORREOS_RECUPERACION =
            new RateLimiter.Limite("olvide-cuenta", 3, java.time.Duration.ofMinutes(15));

    @Inject UsuarioRepository usuarios;
    @Inject PasswordHasher hasher;
    @Inject TokenService tokens;
    @Inject AccesoService accesos;
    @Inject IntentosFallidos intentosFallidos;
    @Inject RateLimiter rateLimiter;
    @Inject RecaptchaService recaptcha;
    @Inject PasswordResetService passwordReset;
    @Inject PoliticaPassword politica;

    /** Sesión iniciada: el cuerpo de la respuesta y el refresh token para la cookie. */
    public record SesionIniciada(String accessToken, long expiresIn, UsuarioSesionDto usuario, RefreshEmitido refresh) {
    }

    /** Login rechazado: estado HTTP, mensaje y, si es un bloqueo, en cuántos segundos reintentar. */
    public static class LoginRechazado extends RuntimeException {
        public final int status;
        public final long reintentarEnSegundos;

        LoginRechazado(int status, String mensaje, long reintentarEnSegundos) {
            super(mensaje, null, false, false);
            this.status = status;
            this.reintentarEnSegundos = reintentarEnSegundos;
        }
    }

    @Transactional
    public SesionIniciada login(String username, String password, String recaptchaToken, String ip, String userAgent) {
        String nombre = username == null ? "" : username.trim().toLowerCase();

        if (!recaptcha.verificar(recaptchaToken)) {
            accesos.registrar(nombre, null, MotivoAcceso.RECAPTCHA_INVALIDO, ip, userAgent);
            throw new LoginRechazado(400, MSJ_RECAPTCHA, 0);
        }

        String claveIntentos = "login:" + nombre;
        long bloqueo = intentosFallidos.segundosDeBloqueo(claveIntentos);
        if (bloqueo > 0) {
            accesos.registrar(nombre, null, MotivoAcceso.CUENTA_BLOQUEADA, ip, userAgent);
            throw bloqueada(bloqueo);
        }

        Usuario u = usuarios.porUsernameOEmail(nombre).orElse(null);
        boolean passwordOk;
        if (u == null) {
            hasher.verificarSenuelo(password); // mismo tiempo de respuesta exista o no la cuenta
            passwordOk = false;
        } else {
            passwordOk = hasher.verificar(password, u.getPasswordHash());
        }

        if (!passwordOk) {
            accesos.registrar(nombre, u != null ? u.getId() : null, MotivoAcceso.CREDENCIALES_INVALIDAS, ip, userAgent);
            if (intentosFallidos.registrarFallo(claveIntentos)) {
                throw bloqueada(intentosFallidos.segundosDeBloqueo(claveIntentos));
            }
            throw new LoginRechazado(401, MSJ_CREDENCIALES, 0);
        }
        // Contraseña correcta (aunque la cuenta esté pendiente, rechazada o desactivada).
        intentosFallidos.limpiar(claveIntentos);

        if (u.getRegistroEstado() == EstadoRegistro.PENDIENTE) {
            accesos.registrar(nombre, u.getId(), MotivoAcceso.REGISTRO_PENDIENTE, ip, userAgent);
            throw new LoginRechazado(403, MSJ_PENDIENTE, 0);
        }
        if (u.getRegistroEstado() == EstadoRegistro.RECHAZADO) {
            accesos.registrar(nombre, u.getId(), MotivoAcceso.REGISTRO_RECHAZADO, ip, userAgent);
            throw new LoginRechazado(403, MSJ_RECHAZADO, 0);
        }
        if (!Boolean.TRUE.equals(u.getActivo())) {
            accesos.registrar(nombre, u.getId(), MotivoAcceso.CUENTA_DESACTIVADA, ip, userAgent);
            throw new LoginRechazado(403, MSJ_DESACTIVADA, 0);
        }

        accesos.registrar(nombre, u.getId(), MotivoAcceso.OK, ip, userAgent);
        u.setUltimoAcceso(LocalDateTime.now());
        return iniciar(u, tokens.emitirRefreshToken(u.getId(), ip, userAgent));
    }

    /**
     * Rota el refresh token y emite un access token nuevo. Sin rollback al rechazar: la revocación
     * por reuso (o del token recién rotado de una cuenta desactivada) debe quedar guardada.
     */
    @Transactional(dontRollbackOn = LoginRechazado.class)
    public SesionIniciada renovar(String refreshToken, String ip, String userAgent) {
        TokenService.Rotacion rotacion = tokens.rotar(refreshToken, ip, userAgent);
        if (rotacion == null) {
            throw new LoginRechazado(401, "La sesión expiró. Vuelva a iniciar sesión.", 0);
        }
        Usuario u = usuarios.findById(rotacion.idUsuario());
        // Una cuenta desactivada con la sesión abierta no puede seguir renovando.
        if (u == null || !Boolean.TRUE.equals(u.getActivo())) {
            tokens.revocar(rotacion.nuevo().token());
            throw new LoginRechazado(403, MSJ_DESACTIVADA, 0);
        }
        return iniciar(u, rotacion.nuevo());
    }

    public void cerrarSesion(String refreshToken) {
        tokens.revocar(refreshToken);
    }

    @Transactional
    public UsuarioSesionDto sesion(Long idUsuario) {
        return UsuarioSesionDto.de(usuarios.findById(idUsuario));
    }

    /** "Olvidé mi contraseña": la respuesta es SIEMPRE la misma, exista o no la cuenta. */
    @Transactional
    public void olvidePassword(String usernameOEmail, String recaptchaToken) {
        if (!recaptcha.verificar(recaptchaToken)) throw new ReglaNegocioException(MSJ_RECAPTCHA);
        usuarios.porUsernameOEmail(usernameOEmail)
                .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                // Superado el tope por cuenta no se envía otro correo, pero la respuesta es la misma.
                .filter(u -> rateLimiter.consumir("u:" + u.getId(), CORREOS_RECUPERACION).permitido())
                .ifPresent(passwordReset::solicitar);
    }

    /** Confirma el OTP recibido por correo y fija la nueva contraseña; cierra las demás sesiones. */
    @Transactional
    public void restablecerPassword(String usernameOEmail, String otp, String passwordNueva) {
        Usuario u = usuarios.porUsernameOEmail(usernameOEmail).orElse(null);
        if (u == null) throw new ReglaNegocioException("Código incorrecto.");
        politica.validar(passwordNueva);

        PasswordResetService.OtpCheck check = passwordReset.verificar(u, otp);
        if (check.error() != null) throw new ReglaNegocioException(check.error());

        u.setPasswordHash(hasher.hash(passwordNueva));
        passwordReset.consumir(check.otp());
        tokens.revocarTodos(u.getId());
        intentosFallidos.limpiar("login:" + u.getUsername().toLowerCase());
    }

    private SesionIniciada iniciar(Usuario u, RefreshEmitido refresh) {
        return new SesionIniciada(tokens.emitirAccessToken(u), tokens.vigenciaAccess().toSeconds(),
                UsuarioSesionDto.de(u), refresh);
    }

    private static LoginRechazado bloqueada(long segundos) {
        long minutos = Math.max(1, (segundos + 59) / 60);
        return new LoginRechazado(429, "Demasiados intentos fallidos para este usuario. Por seguridad, espere "
                + minutos + (minutos == 1 ? " minuto" : " minutos")
                + " o recupere su contraseña con «¿Olvidó su contraseña?».", segundos);
    }
}
