import { inject } from '@angular/core';
import { catchError, firstValueFrom, map, of, timeout } from 'rxjs';
import { AuthStore } from '../stores/auth.store';
import { AuthApiService } from './auth-api.service';

/**
 * Corre antes del primer ruteo (provideAppInitializer). Restaura la sesión guardada o, si no hay,
 * intenta renovarla con la cookie del refresh token: así un F5 o un enlace directo no manda al
 * login a quien todavía tiene una sesión válida. Nunca bloquea el arranque más de 5 s.
 */
export function restaurarSesion(): Promise<void> {
  const store = inject(AuthStore);
  const api = inject(AuthApiService);

  store.loadFromStorage();
  if (store.isAuthenticated()) return Promise.resolve();

  return firstValueFrom(
    api.refresh().pipe(
      timeout(5_000),
      map((res) => store.setSession(res.user, res.accessToken)),
      catchError(() => {
        store.clearSession();
        return of(undefined);
      }),
    ),
  );
}
