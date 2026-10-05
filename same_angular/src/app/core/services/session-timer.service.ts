import { Injectable, effect, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthStore } from '../stores/auth.store';
import { AuthApiService } from './auth-api.service';
import { ToastService } from './toast.service';

/** Renueva 60 s antes de que venza el access token, para que el usuario nunca vea un 401. */
const MARGEN_MS = 60_000;
/** Piso para no reprogramar en bucle si el token ya casi venció. */
const MIN_DELAY_MS = 5_000;

/**
 * Mientras haya sesión, renueva el access token antes de que venza. Se activa con solo inyectarlo
 * (ver AppComponent): el effect reacciona a cada token nuevo (login, refresh, otra pestaña) y
 * reprograma el siguiente. Si otra pestaña cierra la sesión, esta vuelve al login.
 */
@Injectable({ providedIn: 'root' })
export class SessionTimerService {
  private readonly authStore = inject(AuthStore);
  private readonly authApi = inject(AuthApiService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private timeoutId: ReturnType<typeof setTimeout> | null = null;
  private teniaSesion = false;

  constructor() {
    effect(() => {
      const token = this.authStore.accessToken();
      this.reprogramar(token);
      if (!token && this.teniaSesion && !this.router.url.startsWith('/auth')) {
        this.router.navigate(['/auth/login']);
      }
      this.teniaSesion = !!token;
    });
  }

  private reprogramar(token: string | null): void {
    if (this.timeoutId != null) {
      clearTimeout(this.timeoutId);
      this.timeoutId = null;
    }
    if (!token) return;
    const expira = this.expiracionMs(token);
    if (expira == null) return;
    const delay = Math.max(expira - Date.now() - MARGEN_MS, MIN_DELAY_MS);
    this.timeoutId = setTimeout(() => this.renovar(), delay);
  }

  private renovar(): void {
    this.authApi.refresh().subscribe({
      // setSession dispara el effect de arriba, que programa la siguiente renovación.
      next: (res) => this.authStore.setSession(res.user, res.accessToken),
      error: () => {
        this.authStore.clearSession();
        this.toast.warning('Su sesión ha finalizado. Vuelva a iniciar sesión.');
        this.router.navigate(['/auth/login']);
      },
    });
  }

  private expiracionMs(token: string): number | null {
    try {
      const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      const json = JSON.parse(atob(payload));
      return typeof json.exp === 'number' ? json.exp * 1000 : null;
    } catch {
      return null;
    }
  }
}
