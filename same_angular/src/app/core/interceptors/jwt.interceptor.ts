import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthApiService } from '../services/auth-api.service';
import { ToastService } from '../services/toast.service';
import { AuthStore } from '../stores/auth.store';
import { mensajeDeError } from '../utils/http-error';

/**
 * Endpoints públicos del core: no llevan Authorization ni disparan un refresh. Si se les mandara
 * un token vencido, el core respondería 401 aunque el endpoint sea público.
 */
const PUBLICOS = /\/(auth\/(login|refresh|logout|olvide-password|restablecer-password)|usuarios\/autorregistro|roles\/autorregistro)(\?|$)/;

function esPublico(req: HttpRequest<unknown>): boolean {
  if (PUBLICOS.test(req.url)) return true;
  // Catálogos de Redes e IPRESS: lectura pública (los usa el formulario de registro).
  return req.method === 'GET' && /\/(redes|ipress)(\/\d+)?(\?|$)/.test(req.url);
}

/**
 * Agrega el access token a las peticiones al core y, ante un 401, renueva la sesión una vez con
 * el refresh token (cookie httpOnly) y reintenta. Si la renovación falla, cierra la sesión.
 */
export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(environment.apiUrl) || esPublico(req)) return next(req);

  const authStore = inject(AuthStore);
  const authApi = inject(AuthApiService);
  const router = inject(Router);
  const toast = inject(ToastService);

  const conToken = (token: string | null) =>
    token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(conToken(authStore.accessToken())).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status !== 401) return throwError(() => err);
      return authApi.refresh().pipe(
        switchMap((res) => {
          authStore.setSession(res.user, res.accessToken);
          return next(conToken(res.accessToken));
        }),
        catchError((errRefresh: HttpErrorResponse) => {
          authStore.clearSession();
          // 403 = la cuenta fue desactivada con la sesión abierta; el core manda el motivo.
          toast.warning(errRefresh.status === 403
            ? mensajeDeError(errRefresh, 'Su cuenta está desactivada.')
            : 'Su sesión ha finalizado. Vuelva a iniciar sesión.');
          router.navigate(['/auth/login']);
          return throwError(() => errRefresh);
        }),
      );
    }),
  );
};
