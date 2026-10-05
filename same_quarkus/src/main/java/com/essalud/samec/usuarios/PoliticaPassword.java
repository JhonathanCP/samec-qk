package com.essalud.samec.usuarios;

import com.essalud.samec.security.PasswordHasher;
import com.essalud.samec.shared.config.SamecConfig;
import com.essalud.samec.shared.error.ReglaNegocioException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Reglas de una contraseña nueva (autorregistro, restablecer, cambiar). Una sola definición. */
@ApplicationScoped
public class PoliticaPassword {

    @Inject
    SamecConfig config;

    public void validar(String password) {
        int minimo = config.auth().passwordLongitudMinima();
        if (password == null || password.length() < minimo)
            throw new ReglaNegocioException("La contraseña debe tener al menos " + minimo + " caracteres.");
        if (password.length() > PasswordHasher.LONGITUD_MAXIMA)
            throw new ReglaNegocioException("La contraseña admite hasta " + PasswordHasher.LONGITUD_MAXIMA + " caracteres.");
        if (!password.chars().anyMatch(Character::isLetter) || !password.chars().anyMatch(Character::isDigit))
            throw new ReglaNegocioException("La contraseña debe combinar letras y números.");
    }
}
