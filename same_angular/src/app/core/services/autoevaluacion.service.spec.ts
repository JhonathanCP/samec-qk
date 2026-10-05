import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { autoevaluacion } from '../../testing/datos-prueba';
import { AutoevaluacionService } from './autoevaluacion.service';

const API = '/samec-api';

/** El front llama a los endpoints del core tal como los publica (SelfAssessmentResource, EvidenceResource, …). */
describe('AutoevaluacionService', () => {
  let api: AutoevaluacionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(AutoevaluacionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('busca sin enviar los filtros vacíos', () => {
    api.buscar({ facilityId: null, year: 2026, status: 'OPEN', page: 0, size: 20 }).subscribe();
    const req = http.expectOne((r) => r.url === `${API}/self-assessments`);
    expect(req.request.params.has('facilityId')).toBe(false);
    expect(req.request.params.get('year')).toBe('2026');
    expect(req.request.params.get('status')).toBe('OPEN');
    req.flush({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 });
  });

  it('candado: cierra con POST y reabre con DELETE sobre /evidence-lock', () => {
    let autoScored = -1;
    api.cerrarCarga(5).subscribe((r) => (autoScored = r.autoScored));
    const cerrar = http.expectOne(`${API}/self-assessments/5/evidence-lock`);
    expect(cerrar.request.method).toBe('POST');
    cerrar.flush({ assessment: autoevaluacion({ evidenceLocked: true }), autoScored: 3 });
    expect(autoScored).toBe(3);

    let bloqueada = true;
    api.abrirCarga(5).subscribe((a) => (bloqueada = a.evidenceLocked));
    const abrir = http.expectOne(`${API}/self-assessments/5/evidence-lock`);
    expect(abrir.request.method).toBe('DELETE');
    abrir.flush(autoevaluacion());
    expect(bloqueada).toBe(false);
  });

  it('registra la evidencia como multipart con uno o más archivos', () => {
    const a = new File(['%PDF-1.4'], 'plan.pdf', { type: 'application/pdf' });
    const b = new File(['%PDF-1.7'], 'acta.pdf', { type: 'application/pdf' });
    api.registrarEvidencia(5, 77, [a, b], '  ', ' Plan 2026 ').subscribe();
    const req = http.expectOne(`${API}/self-assessments/5/evidence`);
    expect(req.request.method).toBe('POST');
    const form = req.request.body as FormData;
    expect(form.get('auditSourceId')).toBe('77');
    expect((form.getAll('files') as File[]).map((f) => f.name)).toEqual(['plan.pdf', 'acta.pdf']);
    expect(form.has('externalUrl')).toBe(false); // vacío: no se envía
    expect(form.get('notes')).toBe('Plan 2026');
    req.flush({});
  });

  it('acciones del evaluado sobre una evidencia', () => {
    api.agregarArchivos(9, [new File(['x'], 'x.pdf')]).subscribe();
    expect(http.expectOne(`${API}/evidence/9/files`).request.method).toBe('POST');
    api.quitarArchivo(9, 3).subscribe();
    expect(http.expectOne(`${API}/evidence/9/files/3`).request.method).toBe('DELETE');
    api.actualizarDetalle(9, 'https://drive/x', 'nota').subscribe();
    const patch = http.expectOne(`${API}/evidence/9`);
    expect(patch.request.method).toBe('PATCH');
    expect(patch.request.body).toEqual({ externalUrl: 'https://drive/x', notes: 'nota' });
    api.reenviar(9).subscribe();
    expect(http.expectOne(`${API}/evidence/9/resubmit`).request.method).toBe('POST');
    api.retirar(9).subscribe();
    expect(http.expectOne({ method: 'DELETE', url: `${API}/evidence/9` })).toBeTruthy();
  });

  it('acciones del evaluador: revisar, verificar en campo y calificar', () => {
    api.revisarEvidencia(9, 'OBSERVED', 2, 'Año incorrecto').subscribe();
    expect(http.expectOne(`${API}/evidence/9/review`).request.body)
      .toEqual({ verdict: 'OBSERVED', observationTypeId: 2, comment: 'Año incorrecto' });

    api.verificarCondicion(5, 40, true, '  ', [new File(['jpg'], 'foto.jpg')]).subscribe();
    const verificar = http.expectOne(`${API}/self-assessments/5/conditions/40/review`);
    expect(verificar.request.method).toBe('PUT');
    const form = verificar.request.body as FormData;
    expect(form.get('met')).toBe('true');
    expect(form.has('comment')).toBe(false);
    expect(form.getAll('files')).toHaveLength(1);

    api.calificar(5, 101, 1, 'Falta la firma').subscribe();
    const calificar = http.expectOne(`${API}/self-assessments/5/criteria/101/score`);
    expect(calificar.request.method).toBe('PUT');
    expect(calificar.request.body).toEqual({ score: 1, justification: 'Falta la firma' });

    api.quitarFotoVerificacion(8, 4).subscribe();
    expect(http.expectOne(`${API}/condition-reviews/8/files/4`).request.method).toBe('DELETE');
  });

  it('descarga los archivos como blob para verlos en el navegador', () => {
    api.descargarArchivo(12).subscribe();
    const req = http.expectOne((r) => r.url === `${API}/files/12`);
    expect(req.request.params.get('inline')).toBe('true');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['%PDF']));
  });
});
