import {
  ApplicationConfig, provideAppInitializer, provideBrowserGlobalErrorListeners, provideZoneChangeDetection,
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { jwtInterceptor } from './core/interceptors/jwt.interceptor';
import { timeoutInterceptor } from './core/interceptors/timeout.interceptor';
import { restaurarSesion } from './core/services/sesion-inicial';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    // El timeout va después del JWT: cada intento (también el que se repite tras renovar) tiene su tope.
    provideHttpClient(withInterceptors([jwtInterceptor, timeoutInterceptor])),
    provideAppInitializer(restaurarSesion),
  ],
};
