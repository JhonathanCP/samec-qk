package com.essalud.samec.auth.password;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;

import com.essalud.samec.security.ratelimit.IntentosFallidos;
import com.essalud.samec.security.token.TokenService;
import com.essalud.samec.shared.config.SamecConfig;
import com.essalud.samec.shared.correo.CorreoPublisher;
import com.essalud.samec.shared.correo.CorreoRequest;
import com.essalud.samec.usuarios.Usuario;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** OTP de "olvidé mi contraseña": 8 dígitos, un solo uso, vence en samec.auth.otp-minutos. */
@ApplicationScoped
public class PasswordResetService implements PanacheRepositoryBase<PasswordResetOtp, Long> {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Inject
    CorreoPublisher correos;

    @Inject
    IntentosFallidos intentosFallidos;

    @Inject
    SamecConfig config;

    /** Resultado de validar un OTP: el código válido, o el mensaje de error a devolver. */
    public record OtpCheck(PasswordResetOtp otp, String error) {
    }

    /** Genera y envía un OTP nuevo; invalida cualquier OTP previo del usuario. */
    @Transactional
    public void solicitar(Usuario usuario) {
        delete("idUsuario", usuario.getId());
        intentosFallidos.limpiar(claveIntentos(usuario)); // código nuevo, intentos nuevos

        String otp = String.format("%08d", RANDOM.nextInt(100_000_000));
        PasswordResetOtp fila = new PasswordResetOtp();
        fila.setIdUsuario(usuario.getId());
        fila.setOtpHash(TokenService.sha256(otp));
        fila.setExpiraEn(LocalDateTime.now().plusMinutes(config.auth().otpMinutos()));
        fila.setUsado(false);
        fila.setCreatedAt(LocalDateTime.now());
        persist(fila);

        correos.enviar(new CorreoRequest(usuario.getEmail(), "Código para restablecer tu contraseña",
                "password-reset-otp.html", Map.of(
                        "nombre", usuario.nombreCompleto(),
                        "otp", otp,
                        "minutos", config.auth().otpMinutos())));
    }

    /** Valida el OTP. Tras varios códigos incorrectos se invalida: hay que pedir otro. */
    @Transactional
    public OtpCheck verificar(Usuario usuario, String otpIngresado) {
        if (otpIngresado == null || otpIngresado.isBlank())
            return new OtpCheck(null, "Debe indicar el código recibido por correo.");

        PasswordResetOtp fila = find("idUsuario = ?1 and usado = false order by createdAt desc", usuario.getId())
                .firstResult();
        if (fila == null)
            return new OtpCheck(null, "No hay un código activo. Solicítelo nuevamente.");
        if (LocalDateTime.now().isAfter(fila.getExpiraEn()))
            return new OtpCheck(null, "El código ha expirado. Solicítelo nuevamente.");

        String clave = claveIntentos(usuario);
        boolean coincide = MessageDigest.isEqual(
                fila.getOtpHash().getBytes(StandardCharsets.UTF_8),
                TokenService.sha256(otpIngresado.strip()).getBytes(StandardCharsets.UTF_8));
        if (!coincide) {
            if (intentosFallidos.registrarFallo(clave)) {
                fila.setUsado(true);
                intentosFallidos.limpiar(clave);
                return new OtpCheck(null, "Demasiados intentos con un código incorrecto. Solicite un código nuevo.");
            }
            return new OtpCheck(null, "Código incorrecto.");
        }
        intentosFallidos.limpiar(clave);
        return new OtpCheck(fila, null);
    }

    /** Marca el OTP como usado; llamar solo tras actualizar la contraseña. */
    @Transactional
    public void consumir(PasswordResetOtp otp) {
        update("usado = true where id = ?1", otp.getId());
    }

    private static String claveIntentos(Usuario usuario) {
        return "otp-reset:" + usuario.getId();
    }
}
