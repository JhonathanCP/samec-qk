import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Autoevaluacion, AssessmentStatus, Checklist, Evidencia, FacilityCategory, PuntajeCriterio, Resultado,
  ReviewVerdict, SubmissionStatus, TipoObservacion, VerificacionCondicion,
} from '../models/autoevaluacion.model';
import { Pagina } from '../models/pagina.model';
import { ApiService } from './api.service';

/** API de la autoevaluación LEA (SelfAssessmentResource, EvidenceResource, … en el core). */
@Injectable({ providedIn: 'root' })
export class AutoevaluacionService {
  private readonly api = inject(ApiService);

  // ── Autoevaluaciones ──

  buscar(filtros: { facilityId?: number | null; year?: number | null; status?: AssessmentStatus | null; page?: number; size?: number }):
    Observable<Pagina<Autoevaluacion>> {
    return this.api.get<Pagina<Autoevaluacion>>('/self-assessments', filtros);
  }

  iniciar(facilityId: number, periodYear: number, facilityCategory: FacilityCategory): Observable<Autoevaluacion> {
    return this.api.post<Autoevaluacion>('/self-assessments', { facilityId, periodYear, facilityCategory });
  }

  obtener(id: number): Observable<Autoevaluacion> {
    return this.api.get<Autoevaluacion>(`/self-assessments/${id}`);
  }

  checklist(id: number): Observable<Checklist> {
    return this.api.get<Checklist>(`/self-assessments/${id}/checklist`);
  }

  resultado(id: number): Observable<Resultado> {
    return this.api.get<Resultado>(`/self-assessments/${id}/result`);
  }

  /** Candado: el evaluador cierra la carga documental del evaluado; devuelve cuántos criterios se calificaron solos. */
  cerrarCarga(id: number): Observable<{ assessment: Autoevaluacion; autoScored: number }> {
    return this.api.post<{ assessment: Autoevaluacion; autoScored: number }>(`/self-assessments/${id}/evidence-lock`);
  }

  abrirCarga(id: number): Observable<Autoevaluacion> {
    return this.api.delete<Autoevaluacion>(`/self-assessments/${id}/evidence-lock`);
  }

  cerrar(id: number): Observable<Autoevaluacion> {
    return this.api.post<Autoevaluacion>(`/self-assessments/${id}/close`);
  }

  // ── Evidencias (evaluado) ──

  evidenciasPorEstado(id: number, status: SubmissionStatus, page = 0, size = 50): Observable<Pagina<Evidencia>> {
    return this.api.get<Pagina<Evidencia>>(`/self-assessments/${id}/evidence`, { status, page, size });
  }

  registrarEvidencia(id: number, auditSourceId: number, archivos: File[], externalUrl: string, notes: string): Observable<Evidencia> {
    const form = new FormData();
    form.append('auditSourceId', String(auditSourceId));
    archivos.forEach((a) => form.append('files', a, a.name));
    if (externalUrl.trim()) form.append('externalUrl', externalUrl.trim());
    if (notes.trim()) form.append('notes', notes.trim());
    return this.api.postForm<Evidencia>(`/self-assessments/${id}/evidence`, form);
  }

  agregarArchivos(evidenciaId: number, archivos: File[]): Observable<Evidencia> {
    const form = new FormData();
    archivos.forEach((a) => form.append('files', a, a.name));
    return this.api.postForm<Evidencia>(`/evidence/${evidenciaId}/files`, form);
  }

  quitarArchivo(evidenciaId: number, archivoId: number): Observable<Evidencia> {
    return this.api.delete<Evidencia>(`/evidence/${evidenciaId}/files/${archivoId}`);
  }

  actualizarDetalle(evidenciaId: number, externalUrl: string, notes: string): Observable<Evidencia> {
    return this.api.patch<Evidencia>(`/evidence/${evidenciaId}`, { externalUrl, notes });
  }

  reenviar(evidenciaId: number): Observable<Evidencia> {
    return this.api.post<Evidencia>(`/evidence/${evidenciaId}/resubmit`);
  }

  retirar(evidenciaId: number): Observable<void> {
    return this.api.delete<void>(`/evidence/${evidenciaId}`);
  }

  // ── Evaluación (evaluador) ──

  revisarEvidencia(evidenciaId: number, verdict: ReviewVerdict, observationTypeId: number | null, comment: string):
    Observable<Evidencia> {
    return this.api.post<Evidencia>(`/evidence/${evidenciaId}/review`, { verdict, observationTypeId, comment });
  }

  verificarCondicion(id: number, conditionId: number, met: boolean, comment: string, fotos: File[]): Observable<VerificacionCondicion> {
    const form = new FormData();
    form.append('met', String(met));
    if (comment.trim()) form.append('comment', comment.trim());
    fotos.forEach((f) => form.append('files', f, f.name));
    return this.api.putForm<VerificacionCondicion>(`/self-assessments/${id}/conditions/${conditionId}/review`, form);
  }

  quitarFotoVerificacion(verificacionId: number, archivoId: number): Observable<VerificacionCondicion> {
    return this.api.delete<VerificacionCondicion>(`/condition-reviews/${verificacionId}/files/${archivoId}`);
  }

  calificar(id: number, criterionId: number, score: number, justification: string): Observable<PuntajeCriterio> {
    return this.api.put<PuntajeCriterio>(`/self-assessments/${id}/criteria/${criterionId}/score`, { score, justification });
  }

  // ── Archivos y catálogo ──

  descargarArchivo(archivoId: number): Observable<Blob> {
    return this.api.descargar(`/files/${archivoId}`, { inline: true });
  }

  tiposObservacion(): Observable<TipoObservacion[]> {
    return this.api.get<TipoObservacion[]>('/catalog/observation-types', { activeOnly: true });
  }
}
