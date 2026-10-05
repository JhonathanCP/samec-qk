import { Component, Input, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { AuthStore } from '../../../core/stores/auth.store';
import { AuthApiService } from '../../../core/services/auth-api.service';
import { UsuarioService } from '../../../core/services/usuario.service';
import { Permission, PERMISOS_USUARIOS } from '../../../core/constants/permissions';
import { ETIQUETA_ROL } from '../../../core/constants/roles';

export interface NavItem {
  label: string;
  icon: string;
  route: string;
  permission?: Permission;
  permissionsAny?: Permission[];
  /** Contador a mostrar junto al ítem (ver badgeFor). */
  badgeKey?: 'solicitudesPendientes';
  /** Grupo del menú (encabezado en mayúsculas, como en el prototipo); sin grupo = opción principal. */
  grupo?: string;
}

/** Ícono de cada grupo del menú. */
const ICONO_GRUPO: Record<string, string> = {
  'Autoevaluación': 'bi bi-clipboard2-check',
  'Administración': 'bi bi-sliders',
};

/**
 * Menú lateral (diseño del prototipo: fondo blanco, "BIENVENIDO", grupos en mayúsculas).
 * Para agregar un módulo del core: una entrada en NAV_ITEMS con su permiso y su grupo.
 */
const NAV_ITEMS: NavItem[] = [
  { label: 'Inicio', icon: 'bi bi-house', route: '/inicio' },
  { grupo: 'Autoevaluación', label: 'Autoevaluaciones', icon: 'bi bi-clipboard2-check', route: '/autoevaluaciones', permission: 'autoevaluacion.ver' },
  {
    grupo: 'Administración', label: 'Macroprocesos, procesos y criterios', icon: 'bi bi-diagram-3', route: '/catalogo',
    permission: 'catalogos.crud',
  },
  { grupo: 'Administración', label: 'Usuarios', icon: 'bi bi-people', route: '/usuarios', permissionsAny: PERMISOS_USUARIOS },
  {
    grupo: 'Administración', label: 'Solicitudes de registro', icon: 'bi bi-person-plus', route: '/usuarios/solicitudes',
    permission: 'usuarios.solicitudes', badgeKey: 'solicitudesPendientes',
  },
  { grupo: 'Administración', label: 'Historial de accesos', icon: 'bi bi-clock-history', route: '/accesos', permission: 'accesos.ver' },
];

@Component({
  selector: 'app-sidebar',
  imports: [CommonModule, RouterLink],
  template: `
    <aside class="sidebar" [class.collapsed]="collapsed">
      <div class="cabecera">
        @if (!collapsed) {
          <span class="bienvenido">BIENVENIDO</span>
          <span class="dato"><i class="bi bi-person-fill"></i> {{ auth.user()?.nombre }} {{ auth.user()?.apellido }}</span>
          <span class="dato"><i class="bi bi-bank2"></i>
            <span>{{ lugar() }}@if (rolPrincipal()) {<small>{{ rolPrincipal() }}</small>}</span>
          </span>
        } @else {
          <span class="avatar" [title]="(auth.user()?.nombre ?? '') + ' ' + (auth.user()?.apellido ?? '')">{{ iniciales() }}</span>
        }
      </div>

      <nav class="sidebar-nav">
        @for (item of visibles(); track item.route; let i = $index) {
          @if (item.grupo && item.grupo !== visibles()[i - 1]?.grupo) {
            <div class="grupo" [title]="item.grupo">
              <i [class]="iconoGrupo(item.grupo)"></i>
              @if (!collapsed) { <span>{{ item.grupo }}</span> }
            </div>
          }
          <a [routerLink]="item.route" [class.active]="esActivo(item.route)" class="nav-item"
             [class.sub]="!!item.grupo" [class.principal]="!item.grupo" [title]="item.label">
            @if (!item.grupo || collapsed) { <span class="nav-icon"><i [class]="item.icon"></i></span> }
            @if (!collapsed) { <span class="nav-label">{{ item.label }}</span> }
            @if (badgeFor(item); as n) {
              <span class="nav-badge" [class.nav-badge-collapsed]="collapsed">{{ n }}</span>
            }
          </a>
        }
      </nav>

      <div class="sidebar-footer">
        <a routerLink="/mi-cuenta/cambiar-contrasena" class="nav-item principal" [class.active]="esActivo('/mi-cuenta/cambiar-contrasena')" title="Cambiar contraseña">
          <span class="nav-icon"><i class="bi bi-key"></i></span>
          @if (!collapsed) { <span class="nav-label">Cambiar contraseña</span> }
        </a>
        <button class="nav-item principal logout-btn" (click)="logout()" title="Cerrar sesión">
          <span class="nav-icon"><i class="bi bi-box-arrow-right"></i></span>
          @if (!collapsed) { <span class="nav-label">Cerrar sesión</span> }
        </button>
        <div class="marca">
          <img [src]="collapsed ? '/assets/img/logo-essalud-icon.png' : '/assets/img/logo-essalud.png'" alt="EsSalud" />
          @if (!collapsed) { <span>Sistema de Acreditación y Mejora Continua</span> }
        </div>
      </div>
    </aside>
  `,
  styles: [`
    .sidebar {
      width: 272px; min-width: 272px; background: #fff; color: var(--gray-700);
      display: flex; flex-direction: column; height: 100vh; height: 100dvh; overflow: hidden;
      transition: width var(--transition-slow), min-width var(--transition-slow);
      border-right: 1px solid var(--gray-200); box-shadow: 2px 0 12px rgba(15, 23, 42, .04);
    }
    .sidebar.collapsed { width: 68px; min-width: 68px; }
    .cabecera { display: flex; flex-direction: column; gap: 8px; padding: 22px 20px 18px; border-bottom: 1px solid var(--gray-200); flex-shrink: 0; }
    .sidebar.collapsed .cabecera { align-items: center; padding: 18px 8px; }
    .bienvenido { font-size: 25px; font-weight: 900; color: var(--sam-azul); letter-spacing: .02em; margin-bottom: 6px; }
    .dato { display: flex; gap: 10px; align-items: flex-start; font-size: 15px; color: var(--sam-azul); line-height: 1.3; }
    .dato i { font-size: 18px; flex-shrink: 0; width: 20px; text-align: center; }
    .dato small { display: block; font-size: 11.5px; color: var(--gray-500); text-transform: uppercase; letter-spacing: .04em; margin-top: 2px; }
    .avatar {
      width: 38px; height: 38px; border-radius: 50%; background: var(--sam-azul); color: #fff; font-weight: 700;
      display: flex; align-items: center; justify-content: center; font-size: 13px;
    }
    .sidebar-nav { flex: 1; overflow-y: auto; overflow-x: hidden; padding: 12px 0; }
    .grupo {
      display: flex; align-items: center; gap: 12px; padding: 16px 22px 6px; font-size: 13px; font-weight: 700;
      text-transform: uppercase; color: var(--gray-800); letter-spacing: .02em;
    }
    .grupo i { color: var(--sam-azul); font-size: 18px; width: 20px; text-align: center; }
    .sidebar.collapsed .grupo { justify-content: center; padding: 14px 0 4px; }
    .nav-item {
      position: relative; display: flex; align-items: center; gap: 12px; padding: 10px 22px; width: 100%;
      color: var(--gray-700); text-decoration: none; cursor: pointer; border: none; background: none; text-align: left;
      font-size: 14px; border-top: 1px solid transparent; border-bottom: 1px solid transparent;
      transition: background var(--transition-base), color var(--transition-base);
    }
    .nav-item.principal { text-transform: uppercase; font-size: 13px; font-weight: 700; color: var(--gray-800); }
    .nav-item.sub { padding-left: 54px; }
    .sidebar.collapsed .nav-item { justify-content: center; padding: 10px 0; }
    .nav-item:hover { background: var(--gray-50); color: var(--sam-azul); }
    .nav-item.active { background: var(--sam-fila); border-color: var(--gray-200); color: var(--sam-azul); font-weight: 700; }
    .nav-icon { font-size: 18px; flex-shrink: 0; width: 20px; text-align: center; line-height: 1; color: var(--sam-azul); }
    .nav-label { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .nav-item.sub .nav-label { white-space: normal; line-height: 1.3; }
    .nav-badge {
      flex-shrink: 0; min-width: 18px; height: 18px; padding: 0 5px; display: inline-flex; align-items: center;
      justify-content: center; border-radius: 999px; background: #ef4444; color: #fff; font-size: 10.5px; font-weight: 700;
    }
    .nav-badge-collapsed { position: absolute; top: 3px; left: 38px; min-width: 16px; height: 16px; padding: 0 4px; font-size: 9.5px; }
    .sidebar-footer { border-top: 1px solid var(--gray-200); padding: 8px 0 0; flex-shrink: 0; }
    .logout-btn:hover { background: var(--danger-bg); color: var(--danger-text); }
    .logout-btn:hover .nav-icon { color: var(--danger-text); }
    .marca { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 12px 16px 14px; }
    .marca img { max-width: 120px; height: auto; }
    .sidebar.collapsed .marca img { max-width: 30px; }
    .marca span { font-size: 10.5px; color: var(--gray-400); text-align: center; }
  `],
})
export class SidebarComponent implements OnInit {
  @Input() collapsed = false;

  readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly authApi = inject(AuthApiService);
  private readonly usuarios = inject(UsuarioService);

  readonly solicitudesPendientes = signal(0);

  readonly visibles = computed(() =>
    NAV_ITEMS.filter((item) => {
      if (item.permissionsAny?.length && !this.auth.canAny(item.permissionsAny)) return false;
      return !item.permission || this.auth.can(item.permission);
    }),
  );

  readonly iniciales = computed(() => {
    const u = this.auth.user();
    return u ? `${u.nombre?.[0] ?? ''}${u.apellido?.[0] ?? ''}`.toUpperCase() : '?';
  });

  readonly lugar = computed(() => {
    const u = this.auth.user();
    return u?.ipressNombre ?? u?.redNombre ?? 'EsSalud · Nivel nacional';
  });

  readonly rolPrincipal = computed(() => {
    const rol = this.auth.roles()[0];
    return rol ? ETIQUETA_ROL[rol] : '';
  });

  ngOnInit(): void {
    if (this.auth.can('usuarios.solicitudes')) {
      this.usuarios.solicitudes('PENDIENTE').subscribe({
        next: (filas) => this.solicitudesPendientes.set(filas.length),
        error: () => {},
      });
    }
  }

  iconoGrupo(grupo: string): string {
    return ICONO_GRUPO[grupo] ?? 'bi bi-folder2';
  }

  badgeFor(item: NavItem): number {
    return item.badgeKey === 'solicitudesPendientes' ? this.solicitudesPendientes() : 0;
  }

  /**
   * Resalta el ítem de la ruta actual. Si otro ítem es más específico (/usuarios/solicitudes
   * frente a /usuarios), solo se resalta ese; las subrutas sin ítem propio resaltan al padre.
   */
  esActivo(route: string): boolean {
    const url = this.router.url.split(/[?#]/)[0];
    const coincide = (r: string) => url === r || url.startsWith(r + '/');
    if (!coincide(route)) return false;
    return !this.visibles().some((o) => o.route !== route && o.route.startsWith(route + '/') && coincide(o.route));
  }

  logout(): void {
    // Se espera la respuesta antes de salir: si no, el navegador puede cancelar la petición y la
    // cookie del refresh token quedaría vigente.
    const salir = () => {
      this.auth.clearSession();
      this.router.navigate(['/auth/login']);
    };
    this.authApi.logout().subscribe({ next: salir, error: salir });
  }
}
