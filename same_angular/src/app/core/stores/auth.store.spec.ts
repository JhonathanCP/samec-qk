import { TestBed } from '@angular/core/testing';
import { UsuarioSesion } from '../models/sesion.model';
import { AuthStore } from './auth.store';

const usuario: UsuarioSesion = {
  id: 1, username: 'gestor_ipres', nombre: 'María', apellido: 'Quispe', email: 'm@samec.local',
  roles: ['GESTOR_CALIDAD_IPRES'], permisos: ['usuarios.crudIpress', 'dashboard.ipress'],
  alcance: 'IPRESS', idRed: 7, redNombre: 'RED ASISTENCIAL REBAGLIATI', idIpress: 1, ipressNombre: 'H.N. REBAGLIATI',
};

describe('AuthStore', () => {
  let store: AuthStore;

  beforeEach(() => {
    localStorage.clear();
    store = TestBed.inject(AuthStore);
  });

  it('usa los permisos que manda el core, sin recalcularlos por rol', () => {
    store.setSession(usuario, 'token');
    expect(store.isAuthenticated()).toBe(true);
    expect(store.can('usuarios.crudIpress')).toBe(true);
    expect(store.can('usuarios.crud')).toBe(false);
    expect(store.canAny(['usuarios.crud', 'dashboard.ipress'])).toBe(true);
    expect(store.hasRole('GESTOR_CALIDAD_IPRES')).toBe(true);
  });

  it('restaura la sesión guardada y la borra al cerrarla', () => {
    store.setSession(usuario, 'token');
    const otra = new AuthStore();
    otra.loadFromStorage();
    expect(otra.user()?.username).toBe('gestor_ipres');
    expect(otra.accessToken()).toBe('token');

    otra.clearSession();
    expect(otra.isAuthenticated()).toBe(false);
    expect(localStorage.getItem('samec.accessToken')).toBeNull();
  });
});
