import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot } from '@angular/router';
import { Permission } from '../constants/permissions';
import { AuthStore } from '../stores/auth.store';

/** Exige sesión; si no hay, va al login y vuelve a la página pedida después de ingresar. */
export const authGuard: CanActivateFn = (_route: ActivatedRouteSnapshot, state: RouterStateSnapshot) => {
  const auth = inject(AuthStore);
  if (auth.isAuthenticated()) return true;
  return inject(Router).createUrlTree(['/auth/login'], { queryParams: { volver: state.url } });
};

/** Las pantallas de login/registro no tienen sentido con sesión iniciada. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  return auth.isAuthenticated() ? inject(Router).createUrlTree(['/inicio']) : true;
};

/**
 * Exige permisos declarados en data: `permission` (uno obligatorio) y/o `permissionsAny` (alguno).
 * Una ruta hija hereda el data del padre: se exigen ambos para que /usuarios/nuevo no se abra
 * solo porque se puede ver /usuarios.
 */
export const permissionGuard: CanActivateFn = (route: ActivatedRouteSnapshot) => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  const requerido: Permission | undefined = route.data['permission'];
  const alguno: Permission[] | undefined = route.data['permissionsAny'];
  if (alguno?.length && !auth.canAny(alguno)) return router.createUrlTree(['/sin-acceso']);
  if (requerido && !auth.can(requerido)) return router.createUrlTree(['/sin-acceso']);
  return true;
};
