import { Injectable, computed, signal } from '@angular/core';
import { Permission } from '../constants/permissions';
import { RolCodigo, UsuarioSesion } from '../models/sesion.model';

const CLAVE_USUARIO = 'samec.usuario';
const CLAVE_TOKEN = 'samec.accessToken';

/**
 * Estado de la sesión. El access token (vida corta) se guarda en localStorage para sobrevivir a
 * un F5; el refresh token NO pasa por aquí: vive en una cookie httpOnly que el navegador maneja.
 * Los permisos llegan resueltos desde el core: el frontend no repite la regla rol → permiso.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly _user = signal<UsuarioSesion | null>(null);
  private readonly _accessToken = signal<string | null>(null);

  readonly user = this._user.asReadonly();
  readonly accessToken = this._accessToken.asReadonly();
  readonly isAuthenticated = computed(() => !!this._accessToken());
  readonly roles = computed<RolCodigo[]>(() => this._user()?.roles ?? []);
  readonly permisos = computed(() => new Set<Permission>(this._user()?.permisos ?? []));
  readonly alcance = computed(() => this._user()?.alcance ?? null);

  constructor() {
    // Otra pestaña inició, renovó o cerró la sesión: se refleja aquí también.
    window.addEventListener('storage', (e) => {
      if (e.key === CLAVE_TOKEN) this._accessToken.set(e.newValue);
      if (e.key === CLAVE_USUARIO) this._user.set(e.newValue ? this.parsear(e.newValue) : null);
    });
  }

  setSession(user: UsuarioSesion, accessToken: string): void {
    this.setUser(user);
    this.updateToken(accessToken);
  }

  setUser(user: UsuarioSesion): void {
    this._user.set(user);
    this.guardar(CLAVE_USUARIO, JSON.stringify(user));
  }

  updateToken(accessToken: string): void {
    this._accessToken.set(accessToken);
    this.guardar(CLAVE_TOKEN, accessToken);
  }

  clearSession(): void {
    this._user.set(null);
    this._accessToken.set(null);
    this.guardar(CLAVE_USUARIO, null);
    this.guardar(CLAVE_TOKEN, null);
  }

  loadFromStorage(): void {
    try {
      const usuario = localStorage.getItem(CLAVE_USUARIO);
      const token = localStorage.getItem(CLAVE_TOKEN);
      if (usuario && token) {
        this._user.set(this.parsear(usuario));
        this._accessToken.set(token);
      }
    } catch {
      // localStorage bloqueado (modo privado estricto): se sigue sin sesión restaurada.
    }
  }

  can(permiso: Permission): boolean {
    return this.permisos().has(permiso);
  }

  canAny(permisos: Permission[]): boolean {
    return permisos.some((p) => this.can(p));
  }

  hasRole(rol: RolCodigo): boolean {
    return this.roles().includes(rol);
  }

  private parsear(json: string): UsuarioSesion | null {
    try {
      return JSON.parse(json) as UsuarioSesion;
    } catch {
      return null;
    }
  }

  private guardar(clave: string, valor: string | null): void {
    try {
      if (valor === null) localStorage.removeItem(clave);
      else localStorage.setItem(clave, valor);
    } catch {
      // Sin almacenamiento la sesión sigue funcionando en memoria.
    }
  }
}
