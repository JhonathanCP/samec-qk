import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { NodoMacroCat } from '../../core/models/catalogo.model';
import { CatalogoService } from '../../core/services/catalogo.service';
import { ModalService } from '../../core/services/modal.service';
import { ToastService } from '../../core/services/toast.service';
import { CatalogoStore } from './catalogo.store';

const API = '/samec-api';

const ARBOL: NodoMacroCat[] = [
  {
    id: 1, code: 'DIR', name: 'Direccionamiento', category: 'MANAGEMENT', scoreWeight: 8, displayOrder: 1,
    standards: [{
      id: 11, code: 'DIR-1', statement: 'Planeamiento',
      criteria: [{
        id: 101, code: 'DIR1-1', statement: 'PEI', type: 'STRUCTURE', applicableCategories: ['II_1'], conditions: 1,
        sourcesToUpload: 0, onSiteConditions: 0, weightTotal: 2,
      }],
    }],
  },
  { id: 2, code: 'ADT', name: 'Apoyo diagnóstico', category: 'SUPPORT', scoreWeight: 2.5, displayOrder: 2, standards: [] },
];

/** Estado del mantenimiento "Macroprocesos, procesos y criterios". */
describe('CatalogoStore', () => {
  let store: CatalogoStore;
  let http: HttpTestingController;

  function cargar(): void {
    store.cargarTodo();
    http.expectOne(`${API}/catalog/tree`).flush(ARBOL);
    http.expectOne(`${API}/catalog/institutional-processes`).flush([
      { id: 1, parentId: null, name: 'Gestión de la calidad' },
      { id: 2, parentId: 1, name: 'Auditoría' },
      { id: 3, parentId: null, name: 'Atención ambulatoria' },
      { id: 4, parentId: 1, name: 'Acreditación' },
    ]);
    http.expectOne((r) => r.url === `${API}/catalog/normative-documents`)
      .flush({ content: [{ id: 7, title: 'NTS 050', url: null }, { id: 8, title: 'Directiva 01', url: null }] });
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), CatalogoStore],
    });
    store = TestBed.inject(CatalogoStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('carga el árbol, los procesos y la normativa', () => {
    cargar();
    expect(store.cargando()).toBe(false);
    expect(store.sumaPesos()).toBe(10.5);
    expect(store.opcionesMacro().map((o) => o.label)).toEqual(['DIR · Direccionamiento', 'ADT · Apoyo diagnóstico']);
    expect(store.opcionesEstandar()).toEqual([{ value: 11, label: 'DIR-1 · Direccionamiento' }]);
    expect(store.opcionesNorma().map((o) => o.label)).toEqual(['Directiva 01', 'NTS 050']);
  });

  it('los procesos se ofrecen con su proceso padre, en orden alfabético', () => {
    cargar();
    expect(store.opcionesProceso().map((o) => o.label)).toEqual([
      'Atención ambulatoria', 'Gestión de la calidad', 'Gestión de la calidad › Acreditación', 'Gestión de la calidad › Auditoría',
    ]);
    expect(store.nombreProceso(2)).toBe('Gestión de la calidad › Auditoría');
    expect(store.nombreProceso(null)).toBe('');
  });

  it('ubica criterios y estándares en el árbol', () => {
    cargar();
    expect(store.ubicarCriterio(101)).toEqual({ macroId: 1, estandarId: 11 });
    expect(store.ubicarCriterio(999)).toBeNull();
    expect(store.ubicarEstandar(11)?.code).toBe('DIR');
  });

  it('si el árbol no carga, muestra el error', () => {
    store.recargarArbol();
    http.expectOne(`${API}/catalog/tree`).flush({ message: 'Sin permiso' }, { status: 403, statusText: 'Forbidden' });
    expect(store.error()).toBe('Sin permiso');
    expect(store.cargando()).toBe(false);
  });

  it('no deja cambios sin guardar sin preguntar', async () => {
    const modal = TestBed.inject(ModalService);
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const confirmar = vi.spyOn(modal, 'confirm').mockResolvedValue(false);

    store.sucio.set(true);
    await store.irA('c-101');
    expect(confirmar).toHaveBeenCalledOnce();
    expect(navegar).not.toHaveBeenCalled();
    expect(store.sucio()).toBe(true);

    confirmar.mockResolvedValue(true);
    await store.irA('c-101', 'procesos');
    expect(store.sucio()).toBe(false);
    expect(navegar).toHaveBeenCalledWith([], { queryParams: { sel: 'c-101', tab: 'procesos' }, queryParamsHandling: 'merge' });

    // sin cambios no pregunta; la pestaña por defecto no queda en la URL
    confirmar.mockClear();
    await store.irA('m-1');
    expect(confirmar).not.toHaveBeenCalled();
    expect(navegar).toHaveBeenLastCalledWith([], { queryParams: { sel: 'm-1', tab: null }, queryParamsHandling: 'merge' });
  });

  it('ejecutar devuelve lo que respondió el core, o null y el mensaje si falló', async () => {
    const api = TestBed.inject(CatalogoService);
    const toast = TestBed.inject(ToastService);

    const borrar = store.ejecutar(api.eliminarEstandar(11), 'Estándar eliminado.');
    expect(store.ocupado()).toBe(true);
    http.expectOne(`${API}/catalog/standards/11`)
      .flush({ message: 'El estándar DIR-1 tiene 1 criterio(s): elimínelos o muévalos primero.' }, { status: 400, statusText: 'Bad Request' });
    expect(await borrar).toBeNull();
    expect(store.ocupado()).toBe(false);
    expect(toast.toasts().at(-1)?.mensaje).toContain('tiene 1 criterio');

    const guardar = store.ejecutar(api.guardarMacro({ id: 2, code: 'ADT', name: 'Apoyo', category: 'SUPPORT', scoreWeight: 2.5, displayOrder: 2 }), 'Guardado.');
    const put = http.expectOne(`${API}/catalog/macro-processes/2`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body.id).toBeUndefined(); // el id va en la URL
    put.flush({ id: 2, code: 'ADT' });
    expect(await guardar).toEqual({ id: 2, code: 'ADT' });
    expect(toast.toasts().at(-1)).toMatchObject({ tipo: 'success', mensaje: 'Guardado.' });
  });
});
