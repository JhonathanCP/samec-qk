import { HttpErrorResponse } from '@angular/common/http';

/**
 * Mensaje para mostrar de un error HTTP. El core y Nginx responden siempre { message }, así que
 * normalmente basta con eso; el resto son respaldos por si no hay cuerpo.
 */
export function mensajeDeError(err: unknown, porDefecto = 'No se pudo completar la operación.'): string {
  if (err instanceof HttpErrorResponse) {
    const mensaje = err.error?.message;
    if (typeof mensaje === 'string' && mensaje.trim()) return mensaje;
    if (err.status === 0) return 'No se pudo conectar con el servidor. Revise su conexión.';
    if (err.status === 403) return 'No tiene permisos para realizar esta acción.';
    if (err.status === 404) return 'El recurso solicitado no existe.';
  }
  return porDefecto;
}
