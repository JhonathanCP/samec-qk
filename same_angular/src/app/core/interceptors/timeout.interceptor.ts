import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { TimeoutError, catchError, throwError, timeout } from 'rxjs';

/**
 * Tiempo máximo de espera por petición. Sin esto, una petición a un backend colgado deja la
 * pantalla en "Cargando..." indefinidamente. Queda unos segundos por encima del de Nginx (60 s)
 * para que normalmente llegue primero el mensaje del servidor; este tope cubre el caso en que ni
 * siquiera Nginx responde.
 */
export const TIMEOUT_GENERAL_MS = 65_000;

export const MENSAJE_TIMEOUT =
  'La solicitud tardó demasiado en responder. Revise su conexión e intente nuevamente.';

export const timeoutInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    timeout(TIMEOUT_GENERAL_MS),
    catchError((err) => {
      if (err instanceof TimeoutError) {
        // Mismo formato que los errores del core ({ message }), que las pantallas ya muestran.
        return throwError(() => new HttpErrorResponse({
          status: 0,
          statusText: 'Timeout',
          url: req.url,
          error: { message: MENSAJE_TIMEOUT },
        }));
      }
      return throwError(() => err);
    }),
  );
