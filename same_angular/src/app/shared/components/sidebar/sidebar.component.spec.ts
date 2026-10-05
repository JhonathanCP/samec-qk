import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthStore } from '../../../core/stores/auth.store';
import { usuario } from '../../../testing/datos-prueba';
import { SidebarComponent } from './sidebar.component';

/** El menú muestra a cada rol solo lo que puede usar (los permisos vienen del core). */
describe('SidebarComponent', () => {
  function menu(tipo: 'admin' | 'evaluador' | 'evaluado'): { opciones: string[]; grupos: string[]; el: HTMLElement } {
    TestBed.inject(AuthStore).setSession(usuario(tipo), 'token');
    const fixture = TestBed.createComponent(SidebarComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    return {
      el,
      opciones: [...el.querySelectorAll('.sidebar-nav .nav-label')].map((n) => n.textContent!.trim()),
      grupos: [...el.querySelectorAll('.sidebar-nav .grupo span')].map((n) => n.textContent!.trim()),
    };
  }

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
  });

  it('evaluador: su IPRESS y la autoevaluación, sin administración', () => {
    const { opciones, grupos, el } = menu('evaluador');
    expect(opciones).toEqual(['Inicio', 'Autoevaluaciones']);
    expect(grupos).toEqual(['Autoevaluación']);
    expect(el.querySelector('.cabecera')?.textContent).toContain('H.N. EDGARDO REBAGLIATI MARTINS');
    expect(el.querySelector('.cabecera small')?.textContent).toBe('Evaluador');
  });

  it('evaluado: lo mismo que el evaluador en el menú', () => {
    expect(menu('evaluado').opciones).toEqual(['Inicio', 'Autoevaluaciones']);
  });

  it('administrador: el catálogo en una sola opción "Macroprocesos, procesos y criterios"', () => {
    const { opciones, grupos, el } = menu('admin');
    expect(opciones).toContain('Macroprocesos, procesos y criterios');
    expect(opciones).toContain('Usuarios');
    expect(grupos).toEqual(['Autoevaluación', 'Administración']);
    expect(el.querySelector('.cabecera')?.textContent).toContain('EsSalud · Nivel nacional');
    TestBed.inject(HttpTestingController).verify(); // sin "usuarios.solicitudes" no consulta pendientes
  });
});
