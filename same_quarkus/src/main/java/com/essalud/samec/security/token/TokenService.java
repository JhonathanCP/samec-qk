package com.essalud.samec.security.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.jboss.logging.Logger;

import com.essalud.samec.shared.config.SamecConfig;
import com.essalud.samec.usuarios.Usuario;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Emite y rota los tokens de sesión.
 * <ul>
 *   <li><b>Access token</b>: JWT RS256 de vida corta (30 min), en el header Authorization. Vive en
 *   el navegador, así que si lo roba un XSS la ventana de uso es acotada.</li>
 *   <li><b>Refresh token</b>: valor aleatorio opaco (no JWT) en cookie httpOnly, de vida larga (24 h
 *   desde el último uso). Cada refresh lo rota; presentar uno ya rotado se trata como robo y cierra
 *   todas las sesiones del usuario.</li>
 * </ul>
 */
@ApplicationScoped
public class TokenService implements PanacheRepositoryBase<RefreshToken, Long> {

    private static final Logger LOG = Logger.getLogger(TokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    @Inject
    SamecConfig config;

    public record RefreshEmitido(String token, Duration vigencia) {
    }

    /** Resultado de rotar: el id del usuario y su nuevo refresh token, o null si no es válido. */
    public record Rotacion(Long idUsuario, RefreshEmitido nuevo) {
    }

    public Duration vigenciaAccess() {
        return Duration.ofMinutes(config.auth().accessTokenMinutos());
    }

    public Duration vigenciaRefresh() {
        return Duration.ofHours(config.auth().refreshTokenHoras());
    }

    public String emitirAccessToken(Usuario usuario) {
        return Jwt.upn(usuario.getUsername())
                .subject(String.valueOf(usuario.getId()))
                .groups(usuario.codigosRol())
                .claim("nombre", usuario.nombreCompleto())
                .expiresIn(vigenciaAccess())
                .sign();
    }

    @Transactional
    public RefreshEmitido emitirRefreshToken(Long idUsuario, String ip, String userAgent) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken fila = new RefreshToken();
        fila.setIdUsuario(idUsuario);
        fila.setTokenHash(sha256(token));
        fila.setExpiraEn(LocalDateTime.now().plus(vigenciaRefresh()));
        fila.setIp(recortar(ip, 45));
        fila.setUserAgent(recortar(userAgent, 255));
        fila.setCreatedAt(LocalDateTime.now());
        persist(fila);
        return new RefreshEmitido(token, vigenciaRefresh());
    }

    /**
     * Valida y rota el refresh token. null si no existe, venció, se revocó o ya se había usado.
     *
     * Si un token ya rotado vuelve a aparecer pasada la ventana de gracia, alguien tiene una copia:
     * se cierran todas las sesiones del usuario (el dueño vuelve a entrar; el atacante queda fuera).
     * Dentro de la ventana (dos pestañas renovando a la vez) se emite otro token sin castigar a nadie.
     */
    @Transactional
    public Rotacion rotar(String token, String ip, String userAgent) {
        if (token == null || token.isBlank()) return null;
        RefreshToken fila = find("tokenHash", sha256(token)).firstResult();
        if (fila == null || fila.getExpiraEn().isBefore(LocalDateTime.now())) return null;

        if (Boolean.TRUE.equals(fila.getRevocado())) {
            if (fila.getRotadoEn() == null) return null; // cerrado por logout/desactivación
            if (fila.getRotadoEn().isAfter(LocalDateTime.now().minusSeconds(config.auth().refreshGraciaSegundos()))) {
                return new Rotacion(fila.getIdUsuario(), emitirRefreshToken(fila.getIdUsuario(), ip, userAgent));
            }
            LOG.warnf("Refresh token reutilizado para el usuario %d (ip %s): se revocan todas sus sesiones.",
                    fila.getIdUsuario(), ip);
            revocarTodos(fila.getIdUsuario());
            return null;
        }

        fila.setRevocado(true);
        fila.setRotadoEn(LocalDateTime.now());
        return new Rotacion(fila.getIdUsuario(), emitirRefreshToken(fila.getIdUsuario(), ip, userAgent));
    }

    @Transactional
    public void revocar(String token) {
        if (token == null || token.isBlank()) return;
        update("revocado = true where tokenHash = ?1", sha256(token));
    }

    /** Cierra todas las sesiones del usuario (desactivación, restablecimiento de contraseña). */
    @Transactional
    public void revocarTodos(Long idUsuario) {
        update("revocado = true where idUsuario = ?1 and revocado = false", idUsuario);
    }

    /** Cierra las demás sesiones y conserva la actual (cambio de la propia contraseña). */
    @Transactional
    public void revocarTodosExcepto(Long idUsuario, String tokenActual) {
        if (tokenActual == null || tokenActual.isBlank()) {
            revocarTodos(idUsuario);
            return;
        }
        update("revocado = true where idUsuario = ?1 and revocado = false and tokenHash <> ?2",
                idUsuario, sha256(tokenActual));
    }

    /** Limpieza de tokens vencidos (los revocados vencen igual a las 24 h). */
    @Transactional
    public long eliminarVencidos() {
        return delete("expiraEn < ?1", LocalDateTime.now());
    }

    public static String sha256(String valor) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String recortar(String valor, int max) {
        return valor == null || valor.length() <= max ? valor : valor.substring(0, max);
    }
}
