import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { usuario } from '../../testing/datos-prueba';
import { AuthStore } from '../stores/auth.store';
import { authGuard, permissionGuard } from './auth.guards';

function ruta(data: Record<string, unknown>): ActivatedRouteSnapshot {
  return { data } as unknown as ActivatedRouteSnapshot;
}

const ESTADO = { url: '/autoevaluaciones/5' } as RouterStateSnapshot;

/** Las rutas del core (autoevaluaciones, catálogo) exigen sesión y el permiso de la ruta. */
describe('guards', () => {
  let auth: AuthStore;
  let router: Router;
  const ejecutar = (guard: typeof authGuard, data: Record<string, unknown> = {}) =>
    TestBed.runInInjectionContext(() => guard(ruta(data), ESTADO));

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    auth = TestBed.inject(AuthStore);
    router = TestBed.inject(Router);
  });

  it('sin sesión va al login y recuerda a dónde volver', () => {
    const r = ejecutar(authGuard) as UrlTree;
    expect(router.serializeUrl(r)).toBe('/auth/login?volver=%2Fautoevaluaciones%2F5');
  });

  it('el evaluado entra a autoevaluaciones pero no al catálogo', () => {
    auth.setSession(usuario('evaluado'), 'token');
    expect(ejecutar(authGuard)).toBe(true);
    expect(ejecutar(permissionGuard, { permission: 'autoevaluacion.ver' })).toBe(true);
    expect(router.serializeUrl(ejecutar(permissionGuard, { permission: 'catalogos.crud' }) as UrlTree)).toBe('/sin-acceso');
  });

  it('el administrador entra al catálogo', () => {
    auth.setSession(usuario('admin'), 'token');
    expect(ejecutar(permissionGuard, { permission: 'catalogos.crud' })).toBe(true);
    expect(ejecutar(permissionGuard, { permissionsAny: ['usuarios.crud', 'usuarios.crudRed'] })).toBe(true);
    expect(router.serializeUrl(ejecutar(permissionGuard, { permission: 'evidencias.registrar' }) as UrlTree)).toBe('/sin-acceso');
  });
});
