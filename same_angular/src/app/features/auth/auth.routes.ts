import { Routes } from '@angular/router';

export const authRoutes: Routes = [
  { path: 'login', loadComponent: () => import('./login/login.component').then((m) => m.LoginComponent) },
  { path: 'registro', loadComponent: () => import('./registro/registro.component').then((m) => m.RegistroComponent) },
  {
    // Los correos de cuenta creada / contraseña restablecida enlazan aquí (con ?usuario=...).
    path: 'olvide-password',
    loadComponent: () => import('./olvide-password/olvide-password.component').then((m) => m.OlvidePasswordComponent),
  },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
];
