package com.essalud.samec.security;

import java.security.SecureRandom;
import java.util.Base64;

import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.enterprise.context.ApplicationScoped;

/** BCrypt (formato $2a$, el mismo de Spring Security en Auditorías). */
@ApplicationScoped
public class PasswordHasher {

    private static final int COSTO = 10;
    /** BCrypt solo considera los primeros 72 bytes: más largo se rechaza en la política. */
    public static final int LONGITUD_MAXIMA = 64;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Hash de una contraseña que nadie conoce: se verifica contra él cuando el usuario no existe,
     * para que el login tarde lo mismo exista o no la cuenta (no revela qué usuarios existen).
     */
    private final String hashSenuelo = hash(aleatoria());

    public String hash(String plano) {
        return BCrypt.withDefaults().hashToString(COSTO, plano.toCharArray());
    }

    public boolean verificar(String plano, String hash) {
        if (plano == null || hash == null) return false;
        try {
            return BCrypt.verifyer().verify(plano.toCharArray(), hash).verified;
        } catch (IllegalArgumentException e) {
            return false; // contraseña de más de 72 bytes o hash con formato inválido
        }
    }

    /** Gasta el mismo tiempo que una verificación real (usuario inexistente). */
    public void verificarSenuelo(String plano) {
        verificar(plano != null ? plano : "", hashSenuelo);
    }

    /** Contraseña aleatoria que nadie conoce: el usuario fija la suya con "¿Olvidó su contraseña?". */
    public static String aleatoria() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
