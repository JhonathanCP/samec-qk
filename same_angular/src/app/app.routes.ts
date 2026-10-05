import { Routes } from '@angular/router';
import { PERMISOS_USUARIOS } from './core/constants/permissions';
import { authGuard, guestGuard, permissionGuard } from './core/guards/auth.guards';

/**
 * Rutas. Los módulos del core se agregan como hijos del layout, con su permiso en data
 * (permissionGuard) y su entrada en el menú (shared/components/sidebar).
 */
export const routes: Routes = [
  {
    path: 'auth',
    canActivate: [guestGuard],
    loadChildren: () => import('./features/auth/auth.routes').then((m) => m.authRoutes),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./shared/components/layout/layout.component').then((m) => m.LayoutComponent),
    children: [
      { path: '', redirectTo: 'inicio', pathMatch: 'full' },
      {
        path: 'inicio',
        loadComponent: () => import('./features/inicio/inicio.component').then((m) => m.InicioComponent),
      },
      {
        path: 'autoevaluaciones',
        canActivate: [permissionGuard],
        data: { permission: 'autoevaluacion.ver' },
        loadChildren: () =>
          import('./features/autoevaluacion/autoevaluacion.routes').then((m) => m.autoevaluacionRoutes),
      },
      {
        path: 'catalogo',
        canActivate: [permissionGuard],
        canDeactivate: [(c: { puedeSalir(): Promise<boolean> }) => c.puedeSalir()],
        data: { permission: 'catalogos.crud' },
        loadComponent: () => import('./features/catalogo/catalogo.component').then((m) => m.CatalogoComponent),
      },
      {
        path: 'usuarios',
        canActivate: [permissionGuard],
        data: { permissionsAny: PERMISOS_USUARIOS },
        loadChildren: () => import('./features/usuarios/usuarios.routes').then((m) => m.usuariosRoutes),
      },
      {
        path: 'accesos',
        canActivate: [permissionGuard],
        data: { permission: 'accesos.ver' },
        loadComponent: () => import('./features/accesos/accesos.component').then((m) => m.AccesosComponent),
      },
      {
        path: 'mi-cuenta/cambiar-contrasena',
        loadComponent: () =>
          import('./features/cuenta/cambiar-password.component').then((m) => m.CambiarPasswordComponent),
      },
      {
        path: 'sin-acceso',
        loadComponent: () =>
          import('./shared/components/sin-acceso/sin-acceso.component').then((m) => m.SinAccesoComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
