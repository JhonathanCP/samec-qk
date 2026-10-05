package com.essalud.samec.shared.error;

import java.time.LocalDateTime;

/** Formato único de error: el frontend muestra {@code message} tal cual. */
public record ErrorResponse(String message, String datetime, String path) {

    public static ErrorResponse de(String mensaje, String path) {
        return new ErrorResponse(mensaje, LocalDateTime.now().toString(), path);
    }
}
