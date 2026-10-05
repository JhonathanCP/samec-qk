package com.essalud.samec.usuarios;

/**
 * Estado de la cuenta respecto del autorregistro. Las cuentas creadas por un administrador nacen
 * APROBADO; las de /auth/registro nacen PENDIENTE hasta que Sede Central las revise.
 */
public enum EstadoRegistro {
    PENDIENTE,
    APROBADO,
    RECHAZADO
}
