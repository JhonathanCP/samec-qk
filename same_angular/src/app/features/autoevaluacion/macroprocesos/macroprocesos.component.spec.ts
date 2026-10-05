import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { Autoevaluacion } from '../../../core/models/autoevaluacion.model';
import { ModalService } from '../../../core/services/modal.service';
import { ToastService } from '../../../core/services/toast.service';
import { AuthStore } from '../../../core/stores/auth.store';
import { autoevaluacion, checklist, resultado, usuario } from '../../../testing/datos-prueba';
import { AutoevaluacionStore } from '../autoevaluacion.store';
import { MacroprocesosComponent } from './macroprocesos.component';

const API = '/samec-api';
const CERRADA = autoevaluacion({ evidenceLocked: true, evidenceLockedAt: '2026-10-05T08:02:00', evidenceLockedByName: 'Juan Pérez' });

describe('MacroprocesosComponent', () => {
  let fixture: ComponentFixture<MacroprocesosComponent>;
  let store: AutoevaluacionStore;
  let http: HttpTestingController;
  let modal: ModalService;

  function mostrar(tipo: 'evaluador' | 'evaluado', a: Autoevaluacion = autoevaluacion()): HTMLElement {
    TestBed.inject(AuthStore).setSession(usuario(tipo), 'token');
    store.id.set(5);
    store.checklist.set(checklist(a));
    store.resultado.set(resultado(a));
    store.cargando.set(false);
    fixture = TestBed.createComponent(MacroprocesosComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const interruptor = (el: HTMLElement) => el.querySelector<HTMLButtonElement>('button.interruptor');

  /** Tras confirmar, la recarga del checklist y del puntaje. */
  function responderRecarga(a: Autoevaluacion): void {
    http.expectOne(`${API}/self-assessments/5/checklist`).flush(checklist(a));
    http.expectOne(`${API}/self-assessments/5/result`).flush(resultado(a));
  }

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [MacroprocesosComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), AutoevaluacionStore],
    });
    store = TestBed.inject(AutoevaluacionStore);
    http = TestBed.inject(HttpTestingController);
    modal = TestBed.inject(ModalService);
  });

  afterEach(() => http.verify());

  it('lista los macroprocesos con su avance y el recuento de criterios en 0', () => {
    const el = mostrar('evaluador');
    const filas = [...el.querySelectorAll('.tabla-macros tbody tr')].map((tr) => tr.textContent!.replace(/\s+/g, ' ').trim());
    expect(filas).toHaveLength(2);
    expect(filas[0]).toContain('Direccionamiento DIR');
    expect(filas[0]).toContain('1 por revisar');
    expect(el.querySelector('.barras')?.textContent).toContain('Gestión de recursos humanos');
    expect(el.textContent).toContain('Bandeja de revisión');
  });

  it('el evaluador ve el candado de la carga documental del evaluado (abierta)', () => {
    const boton = interruptor(mostrar('evaluador'))!;
    expect(boton).not.toBeNull();
    expect(boton.textContent).toContain('Carga documental del evaluado: abierta');
    expect(boton.getAttribute('aria-checked')).toBe('true');
    expect(boton.classList).not.toContain('cerrado');
  });

  it('el evaluado no tiene el candado: ve sus sustentos observados', () => {
    const el = mostrar('evaluado');
    expect(interruptor(el)).toBeNull();
    expect(el.textContent).toContain('Sustentos observados');
    expect(el.textContent).not.toContain('Bandeja de revisión');
  });

  it('sin candado si la autoevaluación ya está cerrada', () => {
    expect(interruptor(mostrar('evaluador', autoevaluacion({ status: 'CLOSED' })))).toBeNull();
  });

  it('cerrar la carga: confirma, llama al core y avisa cuántos criterios se calificaron solos', async () => {
    const confirmar = vi.spyOn(modal, 'confirm').mockResolvedValue(true);
    const el = mostrar('evaluador');

    await fixture.componentInstance.alternarCarga();
    expect(confirmar.mock.calls[0][0]).toContain('El evaluado ya no podrá registrar');
    expect(confirmar.mock.calls[0][1]).toBe('Cerrar carga documental del evaluado');
    const req = http.expectOne(`${API}/self-assessments/5/evidence-lock`);
    expect(req.request.method).toBe('POST');
    expect(store.ocupado()).toBe('candado');
    req.flush({ assessment: CERRADA, autoScored: 4 });

    expect(store.ocupado()).toBeNull();
    expect(TestBed.inject(ToastService).toasts().at(-1)).toMatchObject({
      tipo: 'success', mensaje: 'Carga documental del evaluado cerrada. 4 criterio(s) sin sustentos se calificaron automáticamente.',
    });
    responderRecarga(CERRADA);
    fixture.detectChanges();
    const boton = interruptor(el)!;
    expect(boton.textContent).toContain('Carga documental del evaluado: cerrada');
    expect(boton.classList).toContain('cerrado');
    expect(boton.getAttribute('aria-checked')).toBe('false');
  });

  it('si no se confirma, no cambia nada', async () => {
    vi.spyOn(modal, 'confirm').mockResolvedValue(false);
    mostrar('evaluador');
    await fixture.componentInstance.alternarCarga();
    // afterEach: http.verify() comprueba que no hubo llamadas
  });

  it('si el core rechaza el cierre, muestra su mensaje', async () => {
    vi.spyOn(modal, 'confirm').mockResolvedValue(true);
    mostrar('evaluador');
    await fixture.componentInstance.alternarCarga();
    http.expectOne(`${API}/self-assessments/5/evidence-lock`)
      .flush({ message: 'La carga documental del evaluado ya está cerrada' }, { status: 400, statusText: 'Bad Request' });
    expect(store.ocupado()).toBeNull();
    expect(TestBed.inject(ToastService).toasts().at(-1)).toMatchObject({
      tipo: 'error', mensaje: 'La carga documental del evaluado ya está cerrada',
    });
  });

  it('reabrir la carga: avisa que se quitarán las calificaciones automáticas', async () => {
    const confirmar = vi.spyOn(modal, 'confirm').mockResolvedValue(true);
    mostrar('evaluador', CERRADA);

    const accion = fixture.componentInstance.alternarCarga();
    const req = await vi.waitFor(() => http.expectOne(`${API}/self-assessments/5/evidence-lock`));
    expect(req.request.method).toBe('DELETE');
    expect(confirmar.mock.calls[0][0]).toContain('se quitarán');
    expect(confirmar.mock.calls[0][1]).toBe('Reabrir carga documental del evaluado');
    req.flush(autoevaluacion());
    await accion;
    expect(TestBed.inject(ToastService).toasts().at(-1)?.mensaje).toBe('Carga documental del evaluado reabierta.');
    responderRecarga(autoevaluacion());
  });
});
