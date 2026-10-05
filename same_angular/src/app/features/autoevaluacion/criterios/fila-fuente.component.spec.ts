import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { Autoevaluacion, NodoFuente } from '../../../core/models/autoevaluacion.model';
import { ModalService } from '../../../core/services/modal.service';
import { AuthStore } from '../../../core/stores/auth.store';
import { autoevaluacion, checklist, fuente, resultado, usuario } from '../../../testing/datos-prueba';
import { AutoevaluacionStore } from '../autoevaluacion.store';
import { FilaFuenteComponent } from './fila-fuente.component';

const API = '/samec-api';

/** Fila del Anexo 2: qué puede hacer cada rol con una fuente auditable, con y sin candado. */
describe('FilaFuenteComponent', () => {
  let http: HttpTestingController;
  let store: AutoevaluacionStore;

  function fila(tipo: 'evaluador' | 'evaluado', f: NodoFuente, a: Autoevaluacion = autoevaluacion()) {
    TestBed.inject(AuthStore).setSession(usuario(tipo), 'token');
    store.id.set(5);
    store.checklist.set(checklist(a));
    const fixture = TestBed.createComponent(FilaFuenteComponent);
    fixture.componentRef.setInput('fuente', f);
    fixture.componentRef.setInput('tecnica', 'DOCUMENT_REVIEW');
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const botones = () => [...el.querySelectorAll('button')].map((b) => (b.textContent!.trim() || b.title));
    return { fixture, el, botones };
  }

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [FilaFuenteComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), AutoevaluacionStore],
    });
    http = TestBed.inject(HttpTestingController);
    store = TestBed.inject(AutoevaluacionStore);
  });

  afterEach(() => http.verify());

  it('el evaluado carga el sustento de una fuente sin documento', () => {
    const { el, botones } = fila('evaluado', fuente(1));
    expect(el.textContent).toContain('Sin documento');
    expect(botones()).toEqual(['Cargar']);
  });

  it('con la carga documental del evaluado cerrada, el evaluado ya no ve "Cargar"', () => {
    const { botones } = fila('evaluado', fuente(1), autoevaluacion({ evidenceLocked: true }));
    expect(botones()).toEqual([]);
  });

  it('el evaluado corrige, reenvía o retira un sustento observado', () => {
    expect(fila('evaluado', fuente(3, 'OBSERVED')).botones()).toEqual(
      ['Cargar', 'Reenviar', 'Enlace y notas', 'Retirar el sustento']);
  });

  it('con el candado puesto ya no corrige ni retira el sustento observado', () => {
    expect(fila('evaluado', fuente(3, 'OBSERVED'), autoevaluacion({ evidenceLocked: true })).botones()).toEqual([]);
  });

  it('una evidencia aprobada ya no se modifica', () => {
    const { el, botones } = fila('evaluado', fuente(1, 'APPROVED'));
    expect(botones()).toEqual([]);
    expect(el.querySelector('.radio.marcado')?.previousElementSibling?.textContent).toBe('SI');
  });

  it('el evaluador revisa lo pendiente aunque la carga esté cerrada', () => {
    const { botones } = fila('evaluador', fuente(2, 'PENDING_REVIEW'), autoevaluacion({ evidenceLocked: true }));
    expect(botones()).toEqual(['Revisar el documento']);
  });

  it('el evaluador observa con un motivo y la fila avisa a la IPRESS', async () => {
    const { fixture } = fila('evaluador', fuente(2, 'PENDING_REVIEW'));
    const componente = fixture.componentInstance;
    componente.abrir('revisar');
    componente.tipoObservacion = 4;
    componente.comentario = 'Falta la firma';
    const accion = componente.observar();
    const req = http.expectOne(`${API}/evidence/2/review`);
    expect(req.request.body).toEqual({ verdict: 'OBSERVED', observationTypeId: 4, comment: 'Falta la firma' });
    req.flush({});
    expect(await accion).toBeUndefined();
    expect(componente.panel()).toBeNull();
    http.expectOne(`${API}/self-assessments/5/checklist`).flush(checklist());
    http.expectOne(`${API}/self-assessments/5/result`).flush(resultado());
  });

  it('el evaluado retira su sustento solo si lo confirma', async () => {
    const confirmar = vi.spyOn(TestBed.inject(ModalService), 'confirm').mockResolvedValue(false);
    const { fixture } = fila('evaluado', fuente(2, 'PENDING_REVIEW'));
    await fixture.componentInstance.retirar();
    expect(confirmar).toHaveBeenCalledOnce();

    confirmar.mockResolvedValue(true);
    const accion = fixture.componentInstance.retirar();
    const req = await vi.waitFor(() => http.expectOne(`${API}/evidence/2`));
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    await accion;
    http.expectOne(`${API}/self-assessments/5/checklist`).flush(checklist());
    http.expectOne(`${API}/self-assessments/5/result`).flush(resultado());
  });
});
