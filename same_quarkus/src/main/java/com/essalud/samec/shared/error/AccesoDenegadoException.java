package com.essalud.samec.shared.error;

/**
 * 403 por una regla de alcance (p. ej. un usuario de otra IPRESS). Para "no tiene el permiso"
 * basta con {@code @PermissionsAllowed}; esta es para las reglas que dependen de los datos.
 */
public class AccesoDenegadoException extends RuntimeException {

    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
