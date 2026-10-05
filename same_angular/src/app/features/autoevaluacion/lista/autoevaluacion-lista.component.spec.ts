import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { Autoevaluacion } from '../../../core/models/autoevaluacion.model';
import { AuthStore } from '../../../core/stores/auth.store';
import { autoevaluacion, usuario } from '../../../testing/datos-prueba';
import { AutoevaluacionListaComponent } from './autoevaluacion-lista.component';

const API = '/samec-api';

describe('AutoevaluacionListaComponent', () => {
  let http: HttpTestingController;

  function mostrar(tipo: 'evaluador' | 'evaluado', filas: Autoevaluacion[]) {
    TestBed.inject(AuthStore).setSession(usuario(tipo), 'token');
    const fixture = TestBed.createComponent(AutoevaluacionListaComponent);
    fixture.detectChanges();
    const req = http.expectOne((r) => r.url === `${API}/self-assessments`);
    // evaluador y evaluado: sin filtro de IPRESS, el core devuelve solo la suya
    expect(req.request.params.has('facilityId')).toBe(false);
    req.flush({ content: filas, totalElements: filas.length, totalPages: 1, page: 0, size: 100 });
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [AutoevaluacionListaComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('marca las autoevaluaciones con la carga documental del evaluado cerrada', () => {
    const el = mostrar('evaluado', [
      autoevaluacion({ id: 5, evidenceLocked: true }),
      autoevaluacion({ id: 6, periodYear: 2025, status: 'CLOSED', evidenceLocked: true, finalScore: 90, classification: 'APPROVED' }),
    ]).nativeElement as HTMLElement;
    const filas = el.querySelectorAll('tbody tr');
    expect(filas).toHaveLength(2);
    expect(filas[0].textContent).toContain('Carga del evaluado cerrada');
    expect(filas[1].textContent).not.toContain('Carga del evaluado cerrada'); // cerrada: ya no aplica
    expect(filas[1].textContent).toContain('90.00%');
  });

  it('el evaluado no inicia autoevaluaciones', () => {
    const el = mostrar('evaluado', []).nativeElement as HTMLElement;
    expect(el.textContent).not.toContain('Iniciar autoevaluación');
    expect(el.textContent).toContain('El evaluador de su IPRESS debe iniciar la autoevaluación del año.');
  });

  it('el evaluador inicia la de su propia IPRESS', () => {
    const fixture = mostrar('evaluador', []);
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const componente = fixture.componentInstance;
    componente.abrirFormulario();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('.nueva input[disabled]')?.value)
      .toBe('H.N. EDGARDO REBAGLIATI MARTINS');

    componente.nuevaCategoria = 'II_1';
    componente.nuevoAnio = 2026;
    componente.iniciar();
    const req = http.expectOne(`${API}/self-assessments`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ facilityId: 1, periodYear: 2026, facilityCategory: 'II_1' });
    req.flush(autoevaluacion({ id: 9 }));
    expect(navegar).toHaveBeenCalledWith(['/autoevaluaciones', 9]);
  });
});
