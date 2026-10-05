import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Autoevaluacion } from '../../../core/models/autoevaluacion.model';
import { AuthStore } from '../../../core/stores/auth.store';
import { autoevaluacion, checklist, resultado, usuario } from '../../../testing/datos-prueba';
import { AutoevaluacionShellComponent } from './autoevaluacion-shell.component';

const API = '/samec-api';
const CERRADA = autoevaluacion({ evidenceLocked: true, evidenceLockedAt: '2026-10-05T08:02:00', evidenceLockedByName: 'Juan Pérez' });

/** El aviso del candado se ve en todas las pantallas de la autoevaluación, para ambos roles. */
describe('AutoevaluacionShellComponent', () => {
  let http: HttpTestingController;

  function abrir(tipo: 'evaluador' | 'evaluado', a: Autoevaluacion): HTMLElement {
    TestBed.inject(AuthStore).setSession(usuario(tipo), 'token');
    const fixture = TestBed.createComponent(AutoevaluacionShellComponent);
    fixture.componentRef.setInput('id', '5');
    fixture.detectChanges();
    http.expectOne(`${API}/self-assessments/5/checklist`).flush(checklist(a));
    http.expectOne(`${API}/self-assessments/5/result`).flush(resultado(a));
    http.expectOne((r) => r.url === `${API}/catalog/observation-types`).flush([]);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [AutoevaluacionShellComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sin candado no hay aviso', () => {
    expect(abrir('evaluado', autoevaluacion()).querySelector('.candado')).toBeNull();
  });

  it('el evaluado sabe que ya no puede subir sustentos, quién cerró y cuándo', () => {
    const aviso = abrir('evaluado', CERRADA).querySelector('.candado')!.textContent!.replace(/\s+/g, ' ');
    expect(aviso).toContain('Carga documental del evaluado cerrada por Juan Pérez el 05/10/2026 08:02.');
    expect(aviso).toContain('Ya no puede registrar ni corregir sustentos; consulte con su evaluador.');
  });

  it('el evaluador ve el mismo aviso, con el efecto sobre el evaluado', () => {
    const aviso = abrir('evaluador', CERRADA).querySelector('.candado')!.textContent!;
    expect(aviso).toContain('El evaluado ya no puede registrar ni corregir sustentos.');
  });

  it('una autoevaluación cerrada no muestra el aviso del candado', () => {
    expect(abrir('evaluador', { ...CERRADA, status: 'CLOSED' }).querySelector('.candado')).toBeNull();
  });

  it('si no se puede cargar, lo dice y ofrece volver', () => {
    TestBed.inject(AuthStore).setSession(usuario('evaluado'), 'token');
    const fixture = TestBed.createComponent(AutoevaluacionShellComponent);
    fixture.componentRef.setInput('id', '5');
    fixture.detectChanges();
    http.expectOne(`${API}/self-assessments/5/checklist`).flush({ message: 'Autoevaluación no encontrada' }, { status: 404, statusText: 'Not Found' });
    http.expectOne(`${API}/self-assessments/5/result`).flush({}, { status: 404, statusText: 'Not Found' });
    http.expectOne((r) => r.url === `${API}/catalog/observation-types`).flush([]);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.alert-error')?.textContent).toContain('Autoevaluación no encontrada');
    expect(el.textContent).toContain('Volver a autoevaluaciones');
  });
});
