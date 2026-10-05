import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import {
  Criterio, DocumentoNormativo, Estandar, MacroProceso, NodoMacroCat, ProcesoInstitucional, TipoObservacionCat, Uso,
} from '../models/catalogo.model';
import { Pagina } from '../models/pagina.model';
import { ApiService } from './api.service';

/** API del catálogo de acreditación (/catalog/... en el core; escribir requiere catalogos.crud). */
@Injectable({ providedIn: 'root' })
export class CatalogoService {
  private readonly api = inject(ApiService);

  arbol(): Observable<NodoMacroCat[]> {
    return this.api.get<NodoMacroCat[]>('/catalog/tree');
  }

  // ── Macroprocesos y estándares ──

  guardarMacro(m: MacroProceso): Observable<MacroProceso> {
    const { id, ...cuerpo } = m;
    return id ? this.api.put<MacroProceso>(`/catalog/macro-processes/${id}`, cuerpo) : this.api.post<MacroProceso>('/catalog/macro-processes', cuerpo);
  }

  eliminarMacro(id: number): Observable<void> {
    return this.api.delete<void>(`/catalog/macro-processes/${id}`);
  }

  guardarEstandar(e: Estandar): Observable<Estandar> {
    const { id, ...cuerpo } = e;
    return id ? this.api.put<Estandar>(`/catalog/standards/${id}`, cuerpo) : this.api.post<Estandar>('/catalog/standards', cuerpo);
  }

  eliminarEstandar(id: number): Observable<void> {
    return this.api.delete<void>(`/catalog/standards/${id}`);
  }

  // ── Criterios (agregado completo) ──

  criterio(id: number): Observable<Criterio> {
    return this.api.get<Criterio>(`/catalog/criteria/${id}`);
  }

  guardarCriterio(c: Criterio): Observable<Criterio> {
    const { id, ...cuerpo } = c;
    return id ? this.api.put<Criterio>(`/catalog/criteria/${id}`, cuerpo) : this.api.post<Criterio>('/catalog/criteria', cuerpo);
  }

  eliminarCriterio(id: number): Observable<void> {
    return this.api.delete<void>(`/catalog/criteria/${id}`);
  }

  // ── Procesos institucionales ──

  procesos(): Observable<ProcesoInstitucional[]> {
    return this.api.get<ProcesoInstitucional[]>('/catalog/institutional-processes');
  }

  guardarProceso(p: ProcesoInstitucional): Observable<ProcesoInstitucional> {
    const { id, ...cuerpo } = p;
    return id ? this.api.put<ProcesoInstitucional>(`/catalog/institutional-processes/${id}`, cuerpo)
      : this.api.post<ProcesoInstitucional>('/catalog/institutional-processes', cuerpo);
  }

  eliminarProceso(id: number): Observable<void> {
    return this.api.delete<void>(`/catalog/institutional-processes/${id}`);
  }

  usosDeProceso(id: number): Observable<Uso[]> {
    return this.api.get<Uso[]>(`/catalog/institutional-processes/${id}/usage`);
  }

  // ── Normativa ──

  normas(): Observable<DocumentoNormativo[]> {
    return this.api.get<Pagina<DocumentoNormativo>>('/catalog/normative-documents', { page: 0, size: 200 }).pipe(map((p) => p.content));
  }

  guardarNorma(n: DocumentoNormativo): Observable<DocumentoNormativo> {
    const { id, ...cuerpo } = n;
    return id ? this.api.put<DocumentoNormativo>(`/catalog/normative-documents/${id}`, cuerpo)
      : this.api.post<DocumentoNormativo>('/catalog/normative-documents', cuerpo);
  }

  eliminarNorma(id: number): Observable<void> {
    return this.api.delete<void>(`/catalog/normative-documents/${id}`);
  }

  usosDeNorma(id: number): Observable<Uso[]> {
    return this.api.get<Uso[]>(`/catalog/normative-documents/${id}/usage`);
  }

  // ── Tipos de observación ──

  tiposObservacion(): Observable<TipoObservacionCat[]> {
    return this.api.get<TipoObservacionCat[]>('/catalog/observation-types');
  }

  guardarTipoObservacion(t: TipoObservacionCat): Observable<TipoObservacionCat> {
    const { id, ...cuerpo } = t;
    return id ? this.api.put<TipoObservacionCat>(`/catalog/observation-types/${id}`, cuerpo)
      : this.api.post<TipoObservacionCat>('/catalog/observation-types', cuerpo);
  }

  eliminarTipoObservacion(id: number): Observable<void> {
    return this.api.delete<void>(`/catalog/observation-types/${id}`);
  }
}
