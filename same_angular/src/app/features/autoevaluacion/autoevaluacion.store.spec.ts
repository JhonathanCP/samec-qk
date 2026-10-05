import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Autoevaluacion } from '../../core/models/autoevaluacion.model';
import { AutoevaluacionService } from '../../core/services/autoevaluacion.service';
import { ToastService } from '../../core/services/toast.service';
import { AuthStore } from '../../core/stores/auth.store';
import { autoevaluacion, checklist, resultado, usuario } from '../../testing/datos-prueba';
import {
  AutoevaluacionStore, avanceDeCriterio, criterioPendiente, porcentaje, semaforoCarga,
} from './autoevaluacion.store';

const API = '/samec-api';

describe('avance de la autoevaluación', () => {
  const c = checklist();
  const [dir11, dir12] = c.macroProcesses[0].standards[0].criteria;
  const grh11 = c.macroProcesses[1].standards[0].criteria[0];

  it('cuenta sustentos, revisiones, verificaciones y puntaje de un criterio', () => {
    expect(avanceDeCriterio(dir11)).toMatchObject({
      criterios: 1, criteriosCalificados: 1, puntaje: 2, sustentosRequeridos: 2, sustentosCargados: 2,
      sustentosAprobados: 1, porRevisar: 1, sustentosObservados: 0, verificaciones: 0, criteriosEnCero: 0,
    });
    expect(avanceDeCriterio(dir12)).toMatchObject({ criteriosCalificados: 0, verificaciones: 1, verificacionesHechas: 0 });
    // una evidencia observada no cuenta como cargada: hay que corregirla
    expect(avanceDeCriterio(grh11)).toMatchObject({
      sustentosRequeridos: 1, sustentosCargados: 0, sustentosObservados: 1, criteriosEnCero: 1,
    });
  });

  it('"Solo pendientes" depende de quién mira', () => {
    // evaluador: algo por revisar, una verificación en campo pendiente o sin puntaje
    expect(criterioPendiente(dir11, true)).toBe(true);
    expect(criterioPendiente(dir12, true)).toBe(true);
    expect(criterioPendiente(grh11, true)).toBe(false);
    // evaluado: sustentos sin cargar (o por corregir)
    expect(criterioPendiente(dir11, false)).toBe(false);
    expect(criterioPendiente(dir12, false)).toBe(false);
    expect(criterioPendiente(grh11, false)).toBe(true);
  });

  it('semáforo de la carga documental y porcentajes', () => {
    expect(semaforoCarga(avanceDeCriterio(dir12))).toBe('gris');
    expect(semaforoCarga(avanceDeCriterio(dir11))).toBe('verde');
    expect(semaforoCarga(avanceDeCriterio(grh11))).toBe('rojo');
    expect(semaforoCarga({ ...avanceDeCriterio(dir11), sustentosCargados: 1 })).toBe('ambar');
    expect(porcentaje(1, 3)).toBe(33);
    expect(porcentaje(5, 0)).toBe(0);
  });
});

describe('AutoevaluacionStore', () => {
  let store: AutoevaluacionStore;
  let http: HttpTestingController;
  let auth: AuthStore;

  function abrir(tipo: 'evaluador' | 'evaluado' | 'admin', a: Autoevaluacion = autoevaluacion()): void {
    auth.setSession(usuario(tipo), 'token');
    store.iniciar(5);
    http.expectOne(`${API}/self-assessments/5/checklist`).flush(checklist(a));
    http.expectOne(`${API}/self-assessments/5/result`).flush(resultado(a));
    http.expectOne((r) => r.url === `${API}/catalog/observation-types` && r.params.get('activeOnly') === 'true')
      .flush([{ id: 1, code: 'OBS', name: 'Documento vencido', active: true }]);
  }

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), AutoevaluacionStore],
    });
    store = TestBed.inject(AutoevaluacionStore);
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthStore);
  });

  afterEach(() => http.verify());

  it('carga el checklist, el puntaje y los tipos de observación', () => {
    expect(store.cargando()).toBe(true);
    abrir('evaluador');
    expect(store.cargando()).toBe(false);
    expect(store.macros().map((m) => m.code)).toEqual(['DIR', 'GRH']);
    expect(store.resultado()?.finalScore).toBe(40);
    expect(store.tiposObservacion()).toHaveLength(1);
    expect(store.avanceTotal()).toMatchObject({
      estandares: 2, criterios: 3, puntaje: 2, sustentosRequeridos: 3, sustentosCargados: 2, porRevisar: 1,
      sustentosObservados: 1, criteriosEnCero: 1,
    });
    expect(store.avancePorMacro().get(2)?.criteriosEnCero).toBe(1);
  });

  it('muestra el error si no se puede cargar', () => {
    auth.setSession(usuario('evaluado'), 'token');
    store.iniciar(5);
    http.expectOne(`${API}/self-assessments/5/checklist`)
      .flush({ message: 'No tiene acceso a esta IPRESS' }, { status: 403, statusText: 'Forbidden' });
    http.expectOne(`${API}/self-assessments/5/result`).flush({}, { status: 403, statusText: 'Forbidden' });
    http.expectOne((r) => r.url === `${API}/catalog/observation-types`).flush([]);
    expect(store.error()).toBe('No tiene acceso a esta IPRESS');
    expect(store.checklist()).toBeNull();
  });

  it('el evaluador valida y tiene el candado; no registra sustentos', () => {
    abrir('evaluador');
    expect(store.esEvaluador()).toBe(true);
    expect(store.puedeEvaluar()).toBe(true);
    expect(store.puedeGestionar()).toBe(true);
    expect(store.puedeRegistrar()).toBe(false);
  });

  it('el evaluado registra sustentos mientras la carga documental está abierta', () => {
    abrir('evaluado');
    expect(store.esEvaluador()).toBe(false);
    expect(store.puedeRegistrar()).toBe(true);
    expect(store.puedeEvaluar()).toBe(false);
    expect(store.cargaCerrada()).toBe(false);
  });

  it('con la carga documental del evaluado cerrada, el evaluado ya no registra', () => {
    abrir('evaluado', autoevaluacion({ evidenceLocked: true, evidenceLockedAt: '2026-10-05T08:02:00', evidenceLockedByName: 'Juan Pérez' }));
    expect(store.cargaCerrada()).toBe(true);
    expect(store.abierta()).toBe(true);
    expect(store.puedeRegistrar()).toBe(false);
  });

  it('con la autoevaluación cerrada nadie modifica nada', () => {
    abrir('evaluado', autoevaluacion({ status: 'CLOSED' }));
    expect(store.puedeRegistrar()).toBe(false);
    auth.setSession(usuario('evaluador'), 'token'); // los permisos se recalculan con la sesión
    expect(store.puedeEvaluar()).toBe(false);
    expect(store.puedeGestionar()).toBe(false);
  });

  it('ubica el criterio de una fuente (para ir desde la bandeja)', () => {
    abrir('evaluador');
    expect(store.ubicarFuente(3)).toEqual({ macroId: 2, criterioId: 201 });
    expect(store.ubicarFuente(999)).toBeNull();
    expect(store.macro(1)?.name).toBe('Direccionamiento');
  });

  it('ejecutar: avisa el resultado y recarga; si falla muestra el mensaje del core', async () => {
    abrir('evaluador');
    const toast = TestBed.inject(ToastService);
    const api = TestBed.inject(AutoevaluacionService);

    const ok = store.ejecutar('candado', api.abrirCarga(5), 'Carga documental del evaluado reabierta.');
    expect(store.ocupado()).toBe('candado');
    http.expectOne({ method: 'DELETE', url: `${API}/self-assessments/5/evidence-lock` }).flush(autoevaluacion());
    expect(await ok).toBe(true);
    expect(store.ocupado()).toBeNull();
    expect(toast.toasts().at(-1)).toMatchObject({ tipo: 'success', mensaje: 'Carga documental del evaluado reabierta.' });
    http.expectOne(`${API}/self-assessments/5/checklist`).flush(checklist());
    http.expectOne(`${API}/self-assessments/5/result`).flush(resultado());

    const falla = store.ejecutar('candado', api.abrirCarga(5), 'no se muestra');
    http.expectOne(`${API}/self-assessments/5/evidence-lock`)
      .flush({ message: 'La carga documental del evaluado ya está abierta' }, { status: 400, statusText: 'Bad Request' });
    expect(await falla).toBe(false);
    expect(toast.toasts().at(-1)).toMatchObject({ tipo: 'error', mensaje: 'La carga documental del evaluado ya está abierta' });
  });
});
