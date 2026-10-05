package com.essalud.samec.shared.error;

/** Regla de negocio incumplida: 400 con el mensaje tal cual (es seguro mostrarlo al usuario). */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
