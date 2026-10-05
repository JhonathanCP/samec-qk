import { Routes } from '@angular/router';
import { permissionGuard } from '../../core/guards/auth.guards';

export const usuariosRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('./usuario-list/usuario-list.component').then((m) => m.UsuarioListComponent),
  },
  {
    path: 'nuevo',
    loadComponent: () => import('./usuario-form/usuario-form.component').then((m) => m.UsuarioFormComponent),
  },
  {
    path: ':id/editar',
    loadComponent: () => import('./usuario-form/usuario-form.component').then((m) => m.UsuarioFormComponent),
  },
  {
    path: 'solicitudes',
    canActivate: [permissionGuard],
    data: { permission: 'usuarios.solicitudes' },
    loadComponent: () =>
      import('./solicitudes-registro/solicitudes-registro.component').then((m) => m.SolicitudesRegistroComponent),
  },
];
